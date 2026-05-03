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
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.GeofenceCalculator;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeofenceMonitoringService {

    static final ScopedValue<String> ENTITY_ID = ScopedValue.newInstance();
    static final ScopedValue<MonitoredEntityType> ENTITY_TYPE = ScopedValue.newInstance();

    private final AreaPort areaPort;
    private final EntityStatePort entityStatePort;
    private final EventDispatchPort eventDispatchPort;
    private final InfluxProjectionPort influxProjectionPort;
    private final PointOfInterestPort poiPort;

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
                Coordinate missionTarget = resolveMissionTarget(area);
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

    private Coordinate resolveMissionTarget(Area area) {
        if (area.action() != AreaAction.MISSION_TRIGGER || area.targetPoiId() == null) return null;
        return poiPort.findById(area.targetPoiId())
                .map(poi -> poi.coordinate())
                .orElse(null);
    }

    private void dispatchAndRecord(AreaTransitionEvent event) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> dispatchTask = executor.submit(() -> eventDispatchPort.dispatch(event));
            Future<?> influxTask   = executor.submit(() -> influxProjectionPort.writeTransition(event));
            await(dispatchTask, "event-hub", event.areaId());
            await(influxTask,   "InfluxDB",  event.areaId());
        }
    }

    private void await(Future<?> future, String target, String areaId) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[entity={} type={}] {} interrupted — area={}",
                    ENTITY_ID.get(), ENTITY_TYPE.get(), target, areaId);
        } catch (ExecutionException e) {
            log.error("[entity={} type={}] {} failed — area={}",
                    ENTITY_ID.get(), ENTITY_TYPE.get(), target, areaId, e.getCause());
        }
    }
}
