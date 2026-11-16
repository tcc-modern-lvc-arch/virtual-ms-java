package io.github.raphonzius.lvc.virtualareas;

import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
// channel address/keepalive: spring.grpc.client.channel.event-hub.*
@ImportGrpcClients(target = "event-hub",
        types = {EventHubGrpc.EventHubBlockingStub.class, EventHubGrpc.EventHubStub.class})
public class VirtualAreasApplication {
    static void main(String[] args) {
        SpringApplication.run(VirtualAreasApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
