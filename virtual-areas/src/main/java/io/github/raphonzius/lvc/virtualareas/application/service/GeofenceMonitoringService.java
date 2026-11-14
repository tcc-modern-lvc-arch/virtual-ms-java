package io.github.raphonzius.lvc.virtualareas.application.service;

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
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.GeofenceCalculator;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GeofenceMonitoringService {

    static final ScopedValue<String> ENTITY_ID = ScopedValue.newInstance();
    static final ScopedValue<MonitoredEntityType> ENTITY_TYPE = ScopedValue.newInstance();

    private final AreaPort areaPort;
    private final EntityStatePort entityStatePort;
    private final TransitionOutbox outbox;
    private final InfluxProjectionPort influxProjectionPort;
    private final PointOfInterestPort poiPort;
    private final ExecutorService sideEffectExecutor;
    private final Duration sideEffectTimeout;
    private final Counter influxFailures;

    public GeofenceMonitoringService(AreaPort areaPort, EntityStatePort entityStatePort,
                                     TransitionOutbox outbox,
                                     InfluxProjectionPort influxProjectionPort,
                                     PointOfInterestPort poiPort,
                                     @Qualifier("sideEffectExecutor") ExecutorService sideEffectExecutor,
                                     @Value("${virtual-areas.dispatch.timeout:5s}") Duration sideEffectTimeout,
                                     MeterRegistry meters) {
        this.areaPort = areaPort;
        this.entityStatePort = entityStatePort;
        this.outbox = outbox;
        this.influxProjectionPort = influxProjectionPort;
        this.poiPort = poiPort;
        this.sideEffectExecutor = sideEffectExecutor;
        this.sideEffectTimeout = sideEffectTimeout;
        this.influxFailures = Counter.builder("virtualareas.influx.failures")
                .description("InfluxDB transition projections that failed or timed out").register(meters);
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
                recordTransition(event);

            } else if (!inside && wasInside) {
                var event = new AreaTransitionEvent.Checkout(
                        area.id(), area.name(), entityId, entityType, position, lvc, timestamp);
                transitions.add(event);
                entityStatePort.remove(entityId, entityType, area.id());
                recordTransition(event);

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
     * CHECKIN/CHECKOUT go to the transactional outbox (same transaction as the entity-state change, delivered
     * to event-hub by the relay); the InfluxDB projection is best-effort, fired asynchronously after commit.
     */
    private void recordTransition(AreaTransitionEvent event) {
        outbox.enqueue(event);
        // ScopedValue bindings do not cross threads (nor survive until afterCommit): capture them here
        // and re-bind inside the async task.
        String entityId = ENTITY_ID.get();
        MonitoredEntityType entityType = ENTITY_TYPE.get();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    projectToInflux(event, entityId, entityType);
                }
            });
        } else {
            projectToInflux(event, entityId, entityType);
        }
    }

    /** Never throws: the transaction is already committed, so a failure here must not reach the caller. */
    private void projectToInflux(AreaTransitionEvent event, String entityId, MonitoredEntityType entityType) {
        try {
            CompletableFuture
                    .runAsync(() -> ScopedValue.where(ENTITY_ID, entityId).where(ENTITY_TYPE, entityType)
                            .run(() -> influxProjectionPort.writeTransition(event)), sideEffectExecutor)
                    .orTimeout(sideEffectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((_, failure) -> {
                        if (failure != null) onInfluxFailure(event, entityId, entityType, failure);
                    });
        } catch (RuntimeException e) { // e.g. RejectedExecutionException while shutting down
            onInfluxFailure(event, entityId, entityType, e);
        }
    }

    private void onInfluxFailure(AreaTransitionEvent event, String entityId, MonitoredEntityType entityType,
                                 Throwable failure) {
        try {
            influxFailures.increment();
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            ScopedValue.where(ENTITY_ID, entityId).where(ENTITY_TYPE, entityType).run(() ->
                    log.error("[entity={} type={}] InfluxDB projection failed — area={}",
                            ENTITY_ID.get(), ENTITY_TYPE.get(), event.areaId(), cause));
        } catch (RuntimeException e) {
            log.error("Failed to report InfluxDB projection failure", e);
        }
    }
}
