package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

import java.util.List;

public record CreateAreaRequest(
        String name,
        String description,
        String areaType,
        List<CoordinateDto> coordinates,
        Double radiusMeters,
        List<String> monitoredEntityTypes,
        String action,
        String targetPoiId,
        boolean patrolZone
) {
}
