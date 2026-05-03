package io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventhub.grpc")
public record EventHubProperties(String host, int port) {
}
