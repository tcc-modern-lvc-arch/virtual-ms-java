package io.github.raphonzius.lvc.virtualareas.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared virtual-thread executor for the event-hub / InfluxDB side effects of geofence transitions. */
@Configuration
public class SideEffectExecutorConfiguration {

    // ExecutorService is AutoCloseable, so Spring closes it (waits for running tasks) on shutdown
    @Bean(name = "sideEffectExecutor", destroyMethod = "close")
    public ExecutorService sideEffectExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("side-effect-", 0).factory());
    }
}
