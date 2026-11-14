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
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
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
 * re-subscribes after an exponential backoff with jitter. The backoff resets as soon as a message is
 * received. {@link #stop()} cancels the live calls and interrupts the threads.
 */
@Component
@Slf4j
public class EventHubSubscriberAdapter implements SmartLifecycle {

    private static final List<MonitoredEntityType> TRACKED = List.of(
            MonitoredEntityType.DRONE, MonitoredEntityType.BUS, MonitoredEntityType.VESSEL);

    private final EventHubGrpc.EventHubStub asyncStub;
    private final GeofenceMonitoringService monitoringService;
    private final EventHubProperties props;

    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private final Map<MonitoredEntityType, ClientCallStreamObserver<SubscribeRequest>> liveCalls =
            new ConcurrentHashMap<>();
    private volatile boolean running;

    public EventHubSubscriberAdapter(EventHubGrpc.EventHubStub asyncStub,
                                     GeofenceMonitoringService monitoringService,
                                     EventHubProperties props) {
        this.asyncStub = asyncStub;
        this.monitoringService = monitoringService;
        this.props = props;
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
                awaitStreamEnd(type, backoff);
                if (!running) break;
                Duration delay = backoff.nextDelay();
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

    private void awaitStreamEnd(MonitoredEntityType type, ExponentialBackoff backoff) throws InterruptedException {
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
                        backoff.reset();
                        handle(event);
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (running) log.warn("event-hub subscription error [{}]: {}", type, t.getMessage());
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
    }

    // ── Event handling ──────────────────────────────────────────────────────────

    void handle(EventRequest event) {
        if (event.getEventKind() != EventKind.MOVE) return;
        // VIRTUAL events are published by EventSimulationService and already processed
        // directly there — skip to avoid double-writing to DB and InfluxDB
        if (event.getLvc() == io.github.raphonzius.lvc.proto.event.LvcOrigin.VIRTUAL) return;

        Optional<MonitoredEntityType> type = ProtoMapper.fromProto(event.getEntityType());
        Optional<LvcOrigin> lvc = ProtoMapper.fromProto(event.getLvc());
        if (type.isEmpty() || lvc.isEmpty()) {
            log.warn("Ignoring MOVE entity={} with unrecognized entity_type={} / lvc={} (newer proto?)",
                    event.getEntityId(), event.getEntityTypeValue(), event.getLvcValue());
            return;
        }
        Optional<Coordinate> position = ProtoMapper.locationOf(event);
        if (position.isEmpty()) return;

        try {
            monitoringService.processMove(event.getEntityId(), type.get(), position.get(), lvc.get(),
                    Instant.ofEpochMilli(event.getTimestampMs()));
        } catch (RuntimeException e) {
            log.error("Error processing MOVE event entity={}", event.getEntityId(), e);
        }
    }
}
