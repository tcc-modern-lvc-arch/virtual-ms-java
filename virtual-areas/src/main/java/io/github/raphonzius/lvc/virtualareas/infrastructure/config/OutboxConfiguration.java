package io.github.raphonzius.lvc.virtualareas.infrastructure.config;

import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {

    @Bean
    Clock outboxClock() {
        return Clock.systemUTC();
    }
}
