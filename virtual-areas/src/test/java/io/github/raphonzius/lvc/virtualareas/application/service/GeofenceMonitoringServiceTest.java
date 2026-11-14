package io.github.raphonzius.lvc.virtualareas.application.service;

import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaAction;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaPort;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityState;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityStatePort;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.EventDispatchPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.InfluxProjectionPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.MissionTarget;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionRecord;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PoiType;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterest;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeofenceMonitoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Coordinate CENTER = new Coordinate(-23.5466, -46.6520);
    private static final Area.CircleArea STOP_AREA = new Area.CircleArea("area-1", "Stop", "", CENTER, 80,
            List.of(MonitoredEntityType.BUS), AreaAction.MISSION_TRIGGER, true, "poi-9", false);

    /** Records each side effect together with the ScopedValue bindings visible on the executing thread. */
    record Call(String sink, AreaTransitionEvent event, String scopedEntityId, MonitoredEntityType scopedType,
                boolean virtualThread) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final AreaPort areaPort = mock(AreaPort.class);
    private final EntityStatePort statePort = mock(EntityStatePort.class);
    private final PointOfInterestPort poiPort = mock(PointOfInterestPort.class);
    private ExecutorService executor;
    private EventDispatchPort dispatchPort = e -> record("event-hub", e);
    private InfluxProjectionPort influxPort = new InfluxProjectionPort() {
        @Override
        public void writeTransition(AreaTransitionEvent event) {
            record("influx", event);
        }

        @Override
        public List<TransitionRecord> queryRecent(int limit) {
            return List.of();
        }
    };

    private void record(String sink, AreaTransitionEvent e) {
        calls.add(new Call(sink, e,
                GeofenceMonitoringService.ENTITY_ID.isBound() ? GeofenceMonitoringService.ENTITY_ID.get() : null,
                GeofenceMonitoringService.ENTITY_TYPE.isBound() ? GeofenceMonitoringService.ENTITY_TYPE.get() : null,
                Thread.currentThread().isVirtual()));
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
        return new GeofenceMonitoringService(areaPort, statePort, dispatchPort, influxPort, poiPort,
                executor, timeout);
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
    void sideEffectsRunOnVirtualThreadsWithScopedValuesRebound() {
        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        assertThat(calls).extracting(Call::sink).containsExactlyInAnyOrder("event-hub", "influx");
        assertThat(calls).allSatisfy(c -> {
            assertThat(c.virtualThread()).isTrue();
            assertThat(c.scopedEntityId()).isEqualTo("bus-1");
            assertThat(c.scopedType()).isEqualTo(MonitoredEntityType.BUS);
        });
    }

    @Test
    void sideEffectsAreDeferredUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();

        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        assertThat(calls).as("nothing published before commit").isEmpty();

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(calls).extracting(Call::sink).containsExactlyInAnyOrder("event-hub", "influx");
        assertThat(calls).allSatisfy(c -> assertThat(c.scopedEntityId()).isEqualTo("bus-1"));
    }

    @Test
    void rolledBackTransactionPublishesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        service(Duration.ofSeconds(2)).processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(calls).isEmpty();
    }

    @Test
    void failingOrHangingSinkDoesNotBreakTheCallerAndIsBoundedByTimeout() {
        var release = new CountDownLatch(1);
        dispatchPort = e -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
        influxPort = new InfluxProjectionPort() {
            @Override
            public void writeTransition(AreaTransitionEvent event) {
                throw new IllegalStateException("influx down");
            }

            @Override
            public List<TransitionRecord> queryRecent(int limit) {
                return List.of();
            }
        };

        long start = System.nanoTime();
        var events = service(Duration.ofMillis(200))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);
        release.countDown();

        assertThat(events).hasSize(1);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void stayingInsideOnlyRefreshesState() {
        when(statePort.findCurrentAreasFor("bus-1", MonitoredEntityType.BUS)).thenReturn(List.of(
                new EntityState("bus-1", MonitoredEntityType.BUS, "area-1", NOW.minusSeconds(60), NOW.minusSeconds(5))));

        var events = service(Duration.ofSeconds(2))
                .processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, NOW);

        assertThat(events).isEmpty();
        assertThat(calls).isEmpty();
    }
}
