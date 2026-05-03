package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

public record CreatePoiRequest(
        String name,
        String description,
        String type,
        double lat,
        double lon,
        Double altitudeM,
        String metadata
) {
}
