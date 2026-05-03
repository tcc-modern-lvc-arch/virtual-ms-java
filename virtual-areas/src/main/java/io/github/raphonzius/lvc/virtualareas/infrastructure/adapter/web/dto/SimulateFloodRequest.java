package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

public record SimulateFloodRequest(
        String areaId,
        String severity,
        double waterLevelCm,
        String lvc
) {
}
