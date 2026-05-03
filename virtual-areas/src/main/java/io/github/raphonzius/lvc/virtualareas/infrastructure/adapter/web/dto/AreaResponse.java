package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AreaResponse(
        String id,
        String name,
        String description,
        String areaType,
        List<CoordinateDto> coordinates,
        Double radiusMeters,
        List<String> monitoredEntityTypes,
        String action,
        boolean active,
        String targetPoiId,
        boolean patrolZone
) {
    public static AreaResponse from(Area area) {
        List<String> types = area.monitoredEntityTypes().stream().map(MonitoredEntityType::name).toList();
        return switch (area) {
            case Area.PolygonArea p -> new AreaResponse(
                    p.id(), p.name(), p.description(), "POLYGON",
                    p.vertices().stream().map(c -> new CoordinateDto(c.lat(), c.lon(), c.altitudeM())).toList(),
                    null, types, p.action().name(), p.active(), p.targetPoiId(), p.patrolZone());
            case Area.CircleArea c -> new AreaResponse(
                    c.id(), c.name(), c.description(), "CIRCLE",
                    List.of(new CoordinateDto(c.center().lat(), c.center().lon(), c.center().altitudeM())),
                    c.radiusMeters(), types, c.action().name(), c.active(), c.targetPoiId(), c.patrolZone());
            case Area.CorridorArea co -> new AreaResponse(
                    co.id(), co.name(), co.description(), "CORRIDOR",
                    co.centerline().stream().map(c -> new CoordinateDto(c.lat(), c.lon(), c.altitudeM())).toList(),
                    co.halfWidthMeters(), types, co.action().name(), co.active(), co.targetPoiId(), co.patrolZone());
        };
    }
}
