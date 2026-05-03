package io.github.raphonzius.lvc.virtualareas.domain.event;

import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;

import java.time.Instant;

public interface SimulationEventPort {
    void publishMove(String entityId, MonitoredEntityType entityType,
                     Coordinate position, LvcOrigin lvc, Instant timestamp);

    void publishFlood(String areaId, String severity, double waterLevelCm,
                      Coordinate centroid, LvcOrigin lvc);
}
