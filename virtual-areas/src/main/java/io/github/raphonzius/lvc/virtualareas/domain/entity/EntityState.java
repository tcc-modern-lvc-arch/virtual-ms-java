package io.github.raphonzius.lvc.virtualareas.domain.entity;

import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;

import java.time.Instant;

public record EntityState(
        String entityId,
        MonitoredEntityType entityType,
        String areaId,
        Instant enteredAt,
        Instant lastSeenAt
) {
}
