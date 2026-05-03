package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

public record SimulateMoveRequest(
        String entityId,
        String entityType,
        double lat,
        double lon,
        Double altitudeM,
        String lvc
) {
}
