package io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "influxdb")
public record InfluxDbProperties(String url, String token, String org, String bucket) {
}
