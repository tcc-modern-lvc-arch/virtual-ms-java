package io.github.raphonzius.lvc.virtualareas.application.service;

import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.DispatchProperties;
import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaAction;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaPort;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityState;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityStatePort;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.InfluxProjectionPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.MissionTarget;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionOutbox;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionRecord;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PoiType;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterest;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeofenceMonitoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Coordinate CENTER = new Coordinate(-23.5466, -46.6520);
    private static final Coordinate FAR = new Coordinate(-23.0, -46.0);
    private static final Area.CircleArea STOP_AREA = new Area.CircleArea("area-1", "Stop", "", CENTER, 80,
            List.of(MonitoredEntityType.BUS), AreaAction.MISSION_TRIGGER, true, "poi-9", false);

    /** Records the Influx write together with the ScopedValue bindings visible on the executing thread. */
    record Call(AreaTransitionEvent event, String scopedEntityId, MonitoredEntityType scopedType,
                boolean virtualThread) {
    }

    private final List<Call> influxCalls = new CopyOnWriteArrayList<>();
    private final List<AreaTransitionEvent> outboxRows = new CopyOnWriteArrayList<>();
    private final List<Boolean> syncActiveOnEnqueue = new CopyOnWriteArrayList<>();
    private final AreaPort areaPort = mock(AreaPort.class);
    private final EntityStatePort statePort = mock(EntityStatePort.class);
    private final PointOfInterestPort poiPort = mock(PointOfInterestPort.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private ExecutorService executor;
    private TransitionOutbox outbox = event -> {
        syncActiveOnEnqueue.add(TransactionSynchronizationManager.isSynchronizationActive());
        outboxRows.add(event);
    };
    private InfluxProjectionPort influxPort = influx(e -> influxCalls.add(new Call(e,
            GeofenceMonitoringService.ENTITY_ID.isBound() ? GeofenceMonitoringService.ENTITY_ID.get() : null,
            GeofenceMonitoringService.ENTITY_TYPE.isBound() ? GeofenceMonitoringService.ENTITY_TYPE.get() : null,
            Thread.currentThread().isVirtual())));

    private static InfluxProjectionPort influx(java.util.function.Consumer<AreaTransitionEvent> onWrite) {
        return new InfluxProjectionPort() {
            @Override
            public void writeTransition(AreaTransitionEvent event) {
                onWrite.accept(event);
            }

            @Override
            public List<TransitionRecord> queryRecent(int limit) {
                return List.of();
            }
        };
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        when(areaPort.findActive()).thenReturn(List.of(STOP_AREA));
        when(statePort.findCurrentAreasFor(any(), any())).thenReturn(List.of());
        when(poiPort.findById("poi-9")).thenReturn(Optional.of(new PointOfInterest("poi-9", "Caio Prado", "",
                PoiType.MISSION_TARGET, new Coordinate(-23.55, -46.65, 40.0), null, true)));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        executor.close();
    }

    private GeofenceMonitoringService service(Duration timeout) {
        return new GeofenceMonitoringService(areaPort, statePort, outbox, influxPort, poiPort,
                executor, new DispatchProperties(timeout), meters);
    }

    private double influxFailures() {
        var c = meters.find("virtualareas.influx.failures").counter();
        return c == null ? 0 : c.count();
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
    }

    @Test
    void checkinCarriesMissionTargetPoi() {
        var events = service(Duration.ofSeconds(2))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        assertThat(events).singleElement().isInstanceOfSatisfying(AreaTransitionEvent.Checkin.class,
                c -> assertThat(c.missionTarget()).isEqualTo(
                        new MissionTarget("poi-9", "Caio Prado", new Coordinate(-23.55, -46.65, 40.0))));
    }

    @Test
    void checkinAndCheckoutAreWrittenToTheOutbox() {
        var svc = service(Duration.ofSeconds(2));
        var in = svc.processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        when(statePort.findCurrentAreasFor("bus-1", MonitoredEntityType.BUS)).thenReturn(List.of(
                new EntityState("bus-1", MonitoredEntityType.BUS, "area-1", NOW, NOW)));
        var out = svc.processMove("bus-1", MonitoredEntityType.BUS, FAR, LvcOrigin.LIVE, NOW.plusSeconds(5));

        assertThat(outboxRows).containsExactly(in.getFirst(), out.getFirst());
        assertThat(outboxRows.get(0)).isInstanceOf(AreaTransitionEvent.Checkin.class);
        assertThat(outboxRows.get(1)).isInstanceOf(AreaTransitionEvent.Checkout.class);
    }

    @Test
    void outboxRowIsWrittenInsideTheTransactionNotAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        assertThat(outboxRows).as("enqueued while the transaction is still open").hasSize(1);
        assertThat(syncActiveOnEnqueue).containsExactly(true);
        assertThat(influxCalls).as("influx waits for commit").isEmpty();
    }

    @Test
    void failureToWriteTheOutboxPropagatesSoTheStateChangeRollsBack() {
        outbox = event -> {
            throw new IllegalStateException("db down");
        };

        assertThatThrownBy(() -> service(Duration.ofSeconds(2))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void influxRunsAsyncOnVirtualThreadWithScopedValuesRebound() throws Exception {
        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        await(() -> !influxCalls.isEmpty());
        assertThat(influxCalls).singleElement().satisfies(c -> {
            assertThat(c.virtualThread()).isTrue();
            assertThat(c.scopedEntityId()).isEqualTo("bus-1");
            assertThat(c.scopedType()).isEqualTo(MonitoredEntityType.BUS);
        });
    }

    @Test
    void influxIsDeferredUntilCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        assertThat(influxCalls).as("nothing projected before commit").isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        await(() -> !influxCalls.isEmpty());
        assertThat(influxCalls).singleElement().satisfies(c -> assertThat(c.scopedEntityId()).isEqualTo("bus-1"));
    }

    @Test
    void rolledBackTransactionProjectsNothing() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        Thread.sleep(100);
        assertThat(influxCalls).isEmpty();
    }

    @Test
    void failingInfluxDoesNotAffectCallerOrOutboxAndIsCounted() throws Exception {
        influxPort = influx(e -> {
            throw new IllegalStateException("influx down");
        });

        var events = service(Duration.ofSeconds(2))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        assertThat(events).hasSize(1);
        assertThat(outboxRows).hasSize(1);
        await(() -> influxFailures() >= 1);
        assertThat(influxFailures()).isEqualTo(1);
    }

    @Test
    void influxErrorsAreCapturedToo() throws Exception {
        influxPort = influx(e -> {
            throw new NoSuchMethodError("influx client skew");
        });

        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        await(() -> influxFailures() >= 1);
        assertThat(influxFailures()).isEqualTo(1);
    }

    @Test
    void hangingInfluxNeverBlocksTheCallerAndTimesOut() throws Exception {
        var release = new CountDownLatch(1);
        influxPort = influx(e -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        long start = System.nanoTime();
        var events = service(Duration.ofMillis(150))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(events).hasSize(1);
        assertThat(elapsedMs).as("caller returned without waiting for influx").isLessThan(100);
        await(() -> influxFailures() >= 1);
        release.countDown();
        assertThat(influxFailures()).as("timeout counted as failure").isEqualTo(1);
    }

    @Test
    void afterCommitNeverPropagatesExceptions() {
        TransactionSynchronizationManager.initSynchronization();
        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        executor.close(); // runAsync now throws RejectedExecutionException

        var ran = new AtomicBoolean();
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(s -> {
            s.afterCommit();
            ran.set(true);
        })).doesNotThrowAnyException();
        assertThat(ran).isTrue();
        assertThat(influxFailures()).isEqualTo(1);
    }

    @Test
    void stayingInsideOnlyRefreshesState() throws Exception {
        when(statePort.findCurrentAreasFor("bus-1", MonitoredEntityType.BUS)).thenReturn(List.of(
                new EntityState("bus-1", MonitoredEntityType.BUS, "area-1", NOW.minusSeconds(60), NOW.minusSeconds(5))));

        var events = service(Duration.ofSeconds(2))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        assertThat(events).isEmpty();
        Thread.sleep(50);
        assertThat(outboxRows).isEmpty();
        assertThat(influxCalls).isEmpty();
    }
}
