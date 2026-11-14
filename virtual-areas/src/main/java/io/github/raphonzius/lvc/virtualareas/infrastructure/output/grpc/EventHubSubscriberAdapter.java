package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.SubscribeRequest;
import io.github.raphonzius.lvc.virtualareas.application.service.GeofenceMonitoringService;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.EventHubProperties;
import io.github.raphonzius.lvc.virtualareas.infrastructure.support.LogThrottle;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Keeps one server-streaming {@code Subscribe} call per tracked entity type open against event-hub and
 * feeds MOVE events into {@link GeofenceMonitoringService}.
 * <p>
 * Each stream is driven by its own virtual thread which, when the stream ends (error or completion),
 * re-subscribes after an exponential backoff with jitter. The backoff resets only when the stream that
 * just ended lived at least {@code eventhub.grpc.reconnect-reset-after} (not per message, so a hub that
 * flaps right after sending something cannot cause a hot reconnect loop). {@link #stop()} cancels the live calls and interrupts the threads.
 */
@Component
@Slf4j
public class EventHubSubscriberAdapter implements SmartLifecycle {

    private static final List<MonitoredEntityType> TRACKED = List.of(
            MonitoredEntityType.DRONE, MonitoredEntityType.BUS, MonitoredEntityType.VESSEL);

    private final EventHubGrpc.EventHubStub asyncStub;
    private final GeofenceMonitoringService monitoringService;
    private final EventHubProperties props;
    private final MeterRegistry meters;
    private final LogThrottle warnThrottle = new LogThrottle(Duration.ofMinutes(1));

    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private final Map<MonitoredEntityType, ClientCallStreamObserver<SubscribeRequest>> liveCalls =
            new ConcurrentHashMap<>();
    private volatile boolean running;

    public EventHubSubscriberAdapter(EventHubGrpc.EventHubStub asyncStub,
                                     GeofenceMonitoringService monitoringService,
                                     EventHubProperties props, MeterRegistry meters) {
        this.asyncStub = asyncStub;
        this.monitoringService = monitoringService;
        this.props = props;
        this.meters = meters;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        TRACKED.forEach(type -> workers.add(Thread.ofVirtual()
                .name("grpc-sub-" + type.name().toLowerCase())
                .start(() -> subscribeLoop(type))));
        log.info("Started {} gRPC subscription threads for event-hub", TRACKED.size());
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        liveCalls.values().forEach(call -> call.cancel("virtual-areas shutting down", null));
        workers.forEach(Thread::interrupt);
        log.info("Stopped event-hub subscriptions");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Waits until every subscriber thread has exited (used on shutdown and in tests). */
    boolean awaitTermination(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Thread t : workers) {
            long left = deadline - System.nanoTime();
            if (left <= 0 || !t.join(Duration.ofNanos(left))) return false;
        }
        workers.clear();
        return true;
    }

    // ── Subscription loop ───────────────────────────────────────────────────────

    private void subscribeLoop(MonitoredEntityType type) {
        var backoff = new ExponentialBackoff(props.reconnectInitialBackoff(), props.reconnectMaxBackoff());
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Duration lifetime = awaitStreamEnd(type);
                if (!running) break;
                Duration delay = backoff.nextDelayAfterStream(lifetime, props.reconnectResetAfter());
                log.info("Re-subscribing to event-hub [{}] in {} ms", type, delay.toMillis());
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.error("Unexpected error in [{}] subscriber", type, e);
            }
        }
        liveCalls.remove(type);
        log.debug("Subscriber thread for [{}] exiting", type);
    }

    /** Blocks until the stream ends; returns how long it lived. */
    private Duration awaitStreamEnd(MonitoredEntityType type) throws InterruptedException {
        long startedAt = System.nanoTime();
        var ended = new CompletableFuture<Void>();
        asyncStub.subscribe(
                SubscribeRequest.newBuilder().setEntityType(ProtoMapper.toProto(type)).build(),
                new ClientResponseObserver<SubscribeRequest, EventRequest>() {
                    @Override
                    public void beforeStart(ClientCallStreamObserver<SubscribeRequest> call) {
                        liveCalls.put(type, call);
                        // stop() may have run between the loop check and now
                        if (!running) call.cancel("virtual-areas shutting down", null);
                    }

                    @Override
                    public void onNext(EventRequest event) {
                        handle(event);
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (running) {
                            Status status = Status.fromThrowable(t);
                            log.warn("event-hub subscription error [{}]: {} {}", type, status.getCode(),
                                    status.getDescription());
                        }
                        ended.complete(null);
                    }

                    @Override
                    public void onCompleted() {
                        log.info("event-hub subscription completed [{}]", type);
                        ended.complete(null);
                    }
                });
        try {
            ended.get();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    // ── Event handling ──────────────────────────────────────────────────────────

    void handle(EventRequest event) {
        if (event.getEventKind() != EventKind.MOVE) return;
        // VIRTUAL events are published by EventSimulationService and already processed
        // directly there — skip to avoid double-writing to DB and InfluxDB
        if (event.getLvc() == io.github.raphonzius.lvc.proto.event.LvcOrigin.VIRTUAL) {
            drop("virtual");
            return;
        }

        Optional<MonitoredEntityType> type = ProtoMapper.fromProto(event.getEntityType());
        if (type.isEmpty()) {
            drop("unknown_type");
            if (warnThrottle.allow("unknown_type")) {
                log.warn("Ignoring MOVE entity={} with unrecognized entity_type={} (newer proto?); "
                        + "further occurrences counted, warned at most once a minute",
                        event.getEntityId(), event.getEntityTypeValue());
            }
            return;
        }
        Optional<LvcOrigin> lvc = ProtoMapper.fromProto(event.getLvc());
        if (lvc.isEmpty()) {
            drop("unknown_lvc");
            if (warnThrottle.allow("unknown_lvc")) {
                log.warn("Ignoring MOVE entity={} with unrecognized lvc={} (newer proto?); "
                        + "further occurrences counted, warned at most once a minute",
                        event.getEntityId(), event.getLvcValue());
            }
            return;
        }
        Optional<Coordinate> position = ProtoMapper.locationOf(event);
        if (position.isEmpty()) {
            drop("no_location");
            log.debug("Ignoring MOVE entity={} without location payload", event.getEntityId());
            return;
        }

        try {
            monitoringService.processMove(event.getEntityId(), type.get(), position.get(), lvc.get(),
                    Instant.ofEpochMilli(event.getTimestampMs()));
        } catch (RuntimeException e) {
            drop("error");
            log.error("Error processing MOVE event entity={}", event.getEntityId(), e);
        }
    }

    private void drop(String reason) {
        meters.counter("virtualareas.subscriber.dropped", "reason", reason).increment();
    }
}
