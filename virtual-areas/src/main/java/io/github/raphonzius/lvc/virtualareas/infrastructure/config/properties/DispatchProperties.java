package io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** @param timeout max duration of the async, best-effort InfluxDB projection of one transition */
@ConfigurationProperties(prefix = "virtual-areas.dispatch")
public record DispatchProperties(Duration timeout) {

    public DispatchProperties {
        if (timeout == null) timeout = Duration.ofSeconds(5);
    }
}
