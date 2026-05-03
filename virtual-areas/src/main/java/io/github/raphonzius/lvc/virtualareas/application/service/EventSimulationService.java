package io.github.raphonzius.lvc.virtualareas.application.service;

import io.github.raphonzius.lvc.virtualareas.application.exception.SimulationApplicationException;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaPort;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.SimulationEventPort;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventSimulationService {

    private final GeofenceMonitoringService monitoringService;
    private final SimulationEventPort simulationEventPort;
    private final AreaPort areaPort;

    public List<AreaTransitionEvent> pingMoveEvent(String entityId, String entityTypeName,
                                                   double lat, double lon, Double altM,
                                                   String lvcName) {
        MonitoredEntityType entityType;
        LvcOrigin lvc;
        try {
            entityType = MonitoredEntityType.valueOf(entityTypeName.toUpperCase());
            lvc = LvcOrigin.valueOf(lvcName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SimulationApplicationException(400, "Invalid entityType or lvc: " + e.getMessage());
        }

        Coordinate position = new Coordinate(lat, lon, altM);
        Instant now = Instant.now();

        // Publish raw MOVE to event-hub so other subscribers (constructive-airsim, etc.) receive it
        simulationEventPort.publishMove(entityId, entityType, position, lvc, now);

        // Evaluate geofence and emit CHECKIN/CHECKOUT events
        return monitoringService.processMove(entityId, entityType, position, lvc, now);
    }

    public void simulateFloodEvent(String areaId, String severity, double waterLevelCm, String lvcName) {
        LvcOrigin lvc;
        try {
            lvc = LvcOrigin.valueOf(lvcName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SimulationApplicationException(400, "Invalid lvc: " + lvcName);
        }

        var area = areaPort.findById(areaId).orElseThrow(() ->
                new SimulationApplicationException(404, "Area not found: " + areaId));

        Coordinate centroid = computeCentroid(area);
        simulationEventPort.publishFlood(areaId, severity, waterLevelCm, centroid, lvc);
    }

    private io.github.raphonzius.lvc.virtualareas.domain.area.Area computeCentroidArea(
            io.github.raphonzius.lvc.virtualareas.domain.area.Area area) {
        return area;
    }

    private Coordinate computeCentroid(io.github.raphonzius.lvc.virtualareas.domain.area.Area area) {
        return switch (area) {
            case io.github.raphonzius.lvc.virtualareas.domain.area.Area.PolygonArea p -> {
                double avgLat = p.vertices().stream().mapToDouble(Coordinate::lat).average().orElse(0);
                double avgLon = p.vertices().stream().mapToDouble(Coordinate::lon).average().orElse(0);
                yield new Coordinate(avgLat, avgLon);
            }
            case io.github.raphonzius.lvc.virtualareas.domain.area.Area.CircleArea c -> c.center();
            case io.github.raphonzius.lvc.virtualareas.domain.area.Area.CorridorArea co -> {
                int mid = co.centerline().size() / 2;
                yield co.centerline().get(mid);
            }
        };
    }
}
