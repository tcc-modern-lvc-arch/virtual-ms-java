package io.github.raphonzius.lvc.virtualareas.domain.poi;

import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;

public record PointOfInterest(
        String id,
        String name,
        String description,
        PoiType type,
        Coordinate coordinate,
        String metadata,
        boolean active
) {
}
