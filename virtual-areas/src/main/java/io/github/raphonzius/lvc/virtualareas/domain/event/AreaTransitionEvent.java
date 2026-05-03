package io.github.raphonzius.lvc.virtualareas.domain.event;

import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;

import java.time.Instant;

public sealed interface AreaTransitionEvent
        permits AreaTransitionEvent.Checkin, AreaTransitionEvent.Checkout {

    String areaId();

    String areaName();

    String entityId();

    MonitoredEntityType entityType();

    Coordinate position();

    LvcOrigin lvc();

    Instant timestamp();

    record Checkin(
            String areaId,
            String areaName,
            String entityId,
            MonitoredEntityType entityType,
            Coordinate position,
            LvcOrigin lvc,
            Instant timestamp,
            Coordinate missionTarget  // null when area has no target POI
    ) implements AreaTransitionEvent {
    }

    record Checkout(
            String areaId,
            String areaName,
            String entityId,
            MonitoredEntityType entityType,
            Coordinate position,
            LvcOrigin lvc,
            Instant timestamp
    ) implements AreaTransitionEvent {
    }
}
