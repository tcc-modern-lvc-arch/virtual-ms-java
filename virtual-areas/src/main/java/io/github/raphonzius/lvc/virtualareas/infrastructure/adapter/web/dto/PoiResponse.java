package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterest;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PoiResponse(
        String id,
        String name,
        String description,
        String type,
        double lat,
        double lon,
        Double altitudeM,
        String metadata,
        boolean active
) {
    public static PoiResponse from(PointOfInterest p) {
        return new PoiResponse(
                p.id(), p.name(), p.description(), p.type().name(),
                p.coordinate().lat(), p.coordinate().lon(), p.coordinate().altitudeM(),
                p.metadata(), p.active()
        );
    }
}
