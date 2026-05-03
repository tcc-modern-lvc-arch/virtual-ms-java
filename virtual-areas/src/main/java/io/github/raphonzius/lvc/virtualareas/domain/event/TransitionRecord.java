package io.github.raphonzius.lvc.virtualareas.domain.event;

import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;

import java.time.Instant;

public record TransitionRecord(
        String areaId,
        String areaName,
        String entityId,
        MonitoredEntityType entityType,
        String transition,
        LvcOrigin lvc,
        Instant timestamp
) {
}
