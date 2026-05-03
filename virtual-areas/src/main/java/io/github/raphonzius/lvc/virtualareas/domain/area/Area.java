package io.github.raphonzius.lvc.virtualareas.domain.area;

import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;

import java.util.List;

public sealed interface Area permits Area.PolygonArea, Area.CircleArea, Area.CorridorArea {

    String id();

    String name();

    String description();

    List<MonitoredEntityType> monitoredEntityTypes();

    AreaAction action();

    boolean active();

    String targetPoiId();

    boolean patrolZone();

    record PolygonArea(
            String id,
            String name,
            String description,
            List<Coordinate> vertices,
            List<MonitoredEntityType> monitoredEntityTypes,
            AreaAction action,
            boolean active,
            String targetPoiId,
            boolean patrolZone
    ) implements Area {
    }

    record CircleArea(
            String id,
            String name,
            String description,
            Coordinate center,
            double radiusMeters,
            List<MonitoredEntityType> monitoredEntityTypes,
            AreaAction action,
            boolean active,
            String targetPoiId,
            boolean patrolZone
    ) implements Area {
    }

    record CorridorArea(
            String id,
            String name,
            String description,
            List<Coordinate> centerline,
            double halfWidthMeters,
            List<MonitoredEntityType> monitoredEntityTypes,
            AreaAction action,
            boolean active,
            String targetPoiId,
            boolean patrolZone
    ) implements Area {
    }
}
