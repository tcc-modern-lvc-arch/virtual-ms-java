package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoordinateDto(
        double lat,
        double lon,
        @JsonProperty("altitude_m") Double altitudeM
) {
}
