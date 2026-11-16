package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.DronePayload;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.proto.event.SubscribeRequest;
import io.github.raphonzius.lvc.virtualareas.application.service.GeofenceMonitoringService;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.EventHubProperties;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.github.raphonzius.lvc.virtualareas.LogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class EventHubSubscriberAdapterTest {

    private static final Duration FAST = Duration.ofMillis(20);

    private Server server;
    private ManagedChannel channel;
    private EventHubSubscriberAdapter adapter;
    private final GeofenceMonitoringService monitoring = mock(GeofenceMonitoringService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<ServerCallStreamObserver<EventRequest>> openStreams = new CopyOnWriteArrayList<>();

    /** Starts an in-process event-hub whose Subscribe behaviour for DRONE is given by {@code droneHandler}. */
    private void startHub(BiConsumer<Integer, StreamObserver<EventRequest>> droneHandler) throws Exception {
        var droneCalls = new AtomicInteger();
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(new EventHubGrpc.EventHubImplBase() {
                    @Override
                    public void subscribe(SubscribeRequest req, StreamObserver<EventRequest> obs) {
                        openStreams.add((ServerCallStreamObserver<EventRequest>) obs);
                        if (req.getEntityType() == EntityType.DRONE) {
                            droneHandler.accept(droneCalls.incrementAndGet(), obs);
                        } // other types: keep the stream open, send nothing
                    }
                }).build().start();
        channel = InProcessChannelBuilder.forName(name).build();
        adapter = new EventHubSubscriberAdapter(EventHubGrpc.newStub(channel), monitoring,
                new EventHubProperties(null, FAST, Duration.ofMillis(100), Duration.ofSeconds(30)),
                meters);
        adapter.start();
    }

    @AfterEach
    void tearDown() {
        if (adapter != null) adapter.stop();
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    private static EventRequest drone(EventKind kind, io.github.raphonzius.lvc.proto.event.LvcOrigin lvc) {
        return EventRequest.newBuilder()
                .setEventKind(kind)
                .setEntityType(EntityType.DRONE)
                .setEntityId("drone-7")
                .setLvc(lvc)
                .setTimestampMs(1_700_000_000_000L)
                .setDrone(DronePayload.newBuilder()
                        .setLocation(Location.newBuilder().setLat(-23.5).setLon(-46.6).setAltitudeM(50)))
                .build();
    }

    @Test
    void moveEventIsForwardedToMonitoringService() throws Exception {
        startHub((call, obs) -> obs.onNext(drone(EventKind.MOVE,
                io.github.raphonzius.lvc.proto.event.LvcOrigin.CONSTRUCTIVE)));

        verify(monitoring, timeout(2000)).processMove("drone-7", MonitoredEntityType.DRONE,
                new Coordinate(-23.5, -46.6, 50.0), LvcOrigin.CONSTRUCTIVE,
                Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void virtualAndNonMoveAndUnknownTypeEventsAreIgnored() throws Exception {
        var sent = new CountDownLatch(1);
        startHub((call, obs) -> {
            obs.onNext(drone(EventKind.MOVE, io.github.raphonzius.lvc.proto.event.LvcOrigin.VIRTUAL));
            obs.onNext(drone(EventKind.CHECKIN, io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE));
            obs.onNext(drone(EventKind.MOVE, io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE)
                    .toBuilder().setEntityTypeValue(99).build());
            // marker: a valid event after the ignored ones proves they were consumed, not pending
            obs.onNext(drone(EventKind.MOVE, io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE)
                    .toBuilder().setEntityId("marker").build());
            sent.countDown();
        });

        assertThat(sent.await(2, TimeUnit.SECONDS)).isTrue();
        verify(monitoring, timeout(2000)).processMove(org.mockito.ArgumentMatchers.eq("marker"),
                any(), any(), any(), any());
        verify(monitoring, never()).processMove(org.mockito.ArgumentMatchers.eq("drone-7"),
                any(), any(), any(), any());
    }

    @Test
    void resubscribesAfterStreamError() throws Exception {
        startHub((call, obs) -> {
            if (call == 1) {
                obs.onError(Status.UNAVAILABLE.withDescription("hub restarting").asRuntimeException());
            } else {
                obs.onNext(drone(EventKind.MOVE, io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE));
            }
        });

        verify(monitoring, timeout(3000)).processMove(org.mockito.ArgumentMatchers.eq("drone-7"),
                any(), any(), org.mockito.ArgumentMatchers.eq(LvcOrigin.LIVE), any());
    }

    @Test
    void stopCancelsOpenStreamsAndEndsSubscriberThreads() throws Exception {
        startHub((call, obs) -> { /* keep open */ });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (openStreams.size() < 3 && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(openStreams).hasSize(3);

        adapter.stop();

        assertThat(adapter.isRunning()).isFalse();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (openStreams.stream().anyMatch(s -> !s.isCancelled()) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(openStreams).allMatch(ServerCallStreamObserver::isCancelled);
        assertThat(adapter.awaitTermination(Duration.ofSeconds(2))).isTrue();
    }

    // ── dropped events: counters + log levels ───────────────────────────────────

    private double dropped(String reason) {
        var c = meters.find("virtualareas.subscriber.dropped").tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }

    private EventHubSubscriberAdapter handlerOnly() {
        return new EventHubSubscriberAdapter(null, monitoring, new EventHubProperties(null, null, null, null),
                meters);
    }

    @Test
    void droppedEventsAreCountedPerReason() {
        var handler = handlerOnly();
        var live = io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE;

        handler.handle(drone(EventKind.MOVE, io.github.raphonzius.lvc.proto.event.LvcOrigin.VIRTUAL));
        handler.handle(drone(EventKind.MOVE, live).toBuilder().setEntityTypeValue(99).build());
        handler.handle(drone(EventKind.MOVE, live).toBuilder().setLvcValue(99).build());
        handler.handle(drone(EventKind.MOVE, live).toBuilder().clearDrone().build());
        org.mockito.Mockito.doThrow(new IllegalStateException("db")).when(monitoring)
                .processMove(any(), any(), any(), any(), any());
        handler.handle(drone(EventKind.MOVE, live));
        handler.handle(drone(EventKind.CHECKIN, live)); // not a MOVE: ignored, not a "drop"

        assertThat(dropped("virtual")).isEqualTo(1);
        assertThat(dropped("unknown_type")).isEqualTo(1);
        assertThat(dropped("unknown_lvc")).isEqualTo(1);
        assertThat(dropped("no_location")).isEqualTo(1);
        assertThat(dropped("error")).isEqualTo(1);
    }

    @Test
    void unknownTypeWarnIsRateLimitedAndMissingLocationIsOnlyDebug() {
        var handler = handlerOnly();
        var logs = LogCapture.attach(EventHubSubscriberAdapter.class);
        var live = io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE;

        for (int i = 0; i < 5; i++) {
            handler.handle(drone(EventKind.MOVE, live).toBuilder().setEntityTypeValue(99).build());
            handler.handle(drone(EventKind.MOVE, live).toBuilder().clearDrone().build());
        }

        assertThat(dropped("unknown_type")).as("every drop is counted").isEqualTo(5);
        assertThat(logs.warnings()).as("but at most one WARN per minute").hasSize(1);
        assertThat(logs.at(ch.qos.logback.classic.Level.DEBUG)).hasSize(5); // no_location
        logs.detach();
    }

    @Test
    void streamErrorIsLoggedWithGrpcStatusCodeAndDescription() throws Exception {
        var logs = LogCapture.attach(EventHubSubscriberAdapter.class);
        startHub((call, obs) -> obs.onError(
                Status.UNAVAILABLE.withDescription("hub restarting").asRuntimeException()));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (logs.warnings().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);

        assertThat(logs.warnings().getFirst().getFormattedMessage()).contains("UNAVAILABLE").contains("hub restarting");
        logs.detach();
    }
}
