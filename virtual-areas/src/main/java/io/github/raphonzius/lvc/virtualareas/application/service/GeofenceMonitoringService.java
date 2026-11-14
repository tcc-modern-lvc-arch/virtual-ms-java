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
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.GeofenceCalculator;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GeofenceMonitoringService {

    static final ScopedValue<String> ENTITY_ID = ScopedValue.newInstance();
    static final ScopedValue<MonitoredEntityType> ENTITY_TYPE = ScopedValue.newInstance();

    private final AreaPort areaPort;
    private final EntityStatePort entityStatePort;
    private final EventDispatchPort eventDispatchPort;
    private final InfluxProjectionPort influxProjectionPort;
    private final PointOfInterestPort poiPort;
    private final ExecutorService sideEffectExecutor;
    private final Duration sideEffectTimeout;

    public GeofenceMonitoringService(AreaPort areaPort, EntityStatePort entityStatePort,
                                     EventDispatchPort eventDispatchPort,
                                     InfluxProjectionPort influxProjectionPort,
                                     PointOfInterestPort poiPort,
                                     @Qualifier("sideEffectExecutor") ExecutorService sideEffectExecutor,
                                     @Value("${virtual-areas.dispatch.timeout:5s}") Duration sideEffectTimeout) {
        this.areaPort = areaPort;
        this.entityStatePort = entityStatePort;
        this.eventDispatchPort = eventDispatchPort;
        this.influxProjectionPort = influxProjectionPort;
        this.poiPort = poiPort;
        this.sideEffectExecutor = sideEffectExecutor;
        this.sideEffectTimeout = sideEffectTimeout;
    }

    @Transactional
    public List<AreaTransitionEvent> processMove(String entityId, MonitoredEntityType entityType,
                                                 Coordinate position, LvcOrigin lvc, Instant timestamp) {
        var transitions = new ArrayList<AreaTransitionEvent>();
        ScopedValue.where(ENTITY_ID, entityId)
                .where(ENTITY_TYPE, entityType)
                .run(() -> evaluate(transitions, entityType, position, lvc, timestamp));
        return transitions;
    }

    // Used by AreaGrpcService — pure in-memory check, no DB/event side effects
    public List<Area> checkPoint(Coordinate position, MonitoredEntityType entityType) {
        return areaPort.findActive().stream()
                .filter(a -> a.monitoredEntityTypes().contains(entityType))
                .filter(a -> GeofenceCalculator.isInside(position, a))
                .toList();
    }

    // ── Private ───────────────────────────────────────────────────────────────────

    private void evaluate(List<AreaTransitionEvent> transitions, MonitoredEntityType entityType,
                          Coordinate position, LvcOrigin lvc, Instant timestamp) {
        String entityId = ENTITY_ID.get();

        List<Area> activeAreas = areaPort.findActive().stream()
                .filter(a -> a.monitoredEntityTypes().contains(entityType))
                .toList();

        Map<String, EntityState> currentStates = entityStatePort
                .findCurrentAreasFor(entityId, entityType).stream()
                .collect(Collectors.toMap(EntityState::areaId, s -> s));

        for (Area area : activeAreas) {
            boolean inside = GeofenceCalculator.isInside(position, area);
            boolean wasInside = currentStates.containsKey(area.id());

            if (inside && !wasInside) {
                MissionTarget missionTarget = resolveMissionTarget(area).orElse(null);
                var event = new AreaTransitionEvent.Checkin(
                        area.id(), area.name(), entityId, entityType, position, lvc, timestamp, missionTarget);
                transitions.add(event);
                entityStatePort.upsert(new EntityState(entityId, entityType, area.id(), timestamp, timestamp));
                dispatchAndRecord(event);

            } else if (!inside && wasInside) {
                var event = new AreaTransitionEvent.Checkout(
                        area.id(), area.name(), entityId, entityType, position, lvc, timestamp);
                transitions.add(event);
                entityStatePort.remove(entityId, entityType, area.id());
                dispatchAndRecord(event);

            } else if (inside) {
                EntityState existing = currentStates.get(area.id());
                entityStatePort.upsert(new EntityState(
                        entityId, entityType, area.id(), existing.enteredAt(), timestamp));
            }
        }
    }

    private Optional<MissionTarget> resolveMissionTarget(Area area) {
        if (area.action() != AreaAction.MISSION_TRIGGER || area.targetPoiId() == null) return Optional.empty();
        return poiPort.findById(area.targetPoiId())
                .map(poi -> new MissionTarget(poi.id(), poi.name(), poi.coordinate()));
    }

    /**
     * Publishes the transition to event-hub and InfluxDB. Inside a transaction this waits for commit, so a
     * rolled-back move never leaks an event; without one it runs immediately.
     */
    private void dispatchAndRecord(AreaTransitionEvent event) {
        // ScopedValue bindings do not cross threads (nor survive until afterCommit): capture them here
        // and re-bind inside each task.
        String entityId = ENTITY_ID.get();
        MonitoredEntityType entityType = ENTITY_TYPE.get();
        Runnable publish = () -> fanOut(event, entityId, entityType);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private void fanOut(AreaTransitionEvent event, String entityId, MonitoredEntityType entityType) {
        var dispatch = CompletableFuture.runAsync(
                bound(entityId, entityType, "event-hub", event, () -> eventDispatchPort.dispatch(event)),
                sideEffectExecutor);
        var influx = CompletableFuture.runAsync(
                bound(entityId, entityType, "InfluxDB", event, () -> influxProjectionPort.writeTransition(event)),
                sideEffectExecutor);
        var all = CompletableFuture.allOf(dispatch, influx);
        try {
            all.get(sideEffectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            all.cancel(true);
            log.error("[entity={} type={}] side effects timed out after {} ms — area={}",
                    entityId, entityType, sideEffectTimeout.toMillis(), event.areaId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[entity={} type={}] side effects interrupted — area={}", entityId, entityType, event.areaId());
        } catch (ExecutionException _) {
            // each task already logged its own failure
        }
    }

    private Runnable bound(String entityId, MonitoredEntityType entityType, String target,
                           AreaTransitionEvent event, Runnable task) {
        return () -> ScopedValue.where(ENTITY_ID, entityId).where(ENTITY_TYPE, entityType).run(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("[entity={} type={}] {} failed — area={}",
                        ENTITY_ID.get(), ENTITY_TYPE.get(), target, event.areaId(), e);
            }
        });
    }
}
