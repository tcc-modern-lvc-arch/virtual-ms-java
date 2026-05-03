package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoordinateJson(
        double lat,
        double lon,
        @JsonProperty("altitude_m") Double altitudeM
) {
}
