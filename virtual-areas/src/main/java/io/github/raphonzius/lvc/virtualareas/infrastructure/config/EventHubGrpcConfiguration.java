package io.github.raphonzius.lvc.virtualareas.infrastructure.config;

import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.EventHubProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventHubProperties.class)
public class EventHubGrpcConfiguration {

    @Bean
    public ManagedChannel eventHubChannel(EventHubProperties props) {
        return ManagedChannelBuilder
                .forAddress(props.host(), props.port())
                .usePlaintext()
                .build();
    }

    @Bean
    public EventHubGrpc.EventHubBlockingStub eventHubBlockingStub(ManagedChannel channel) {
        return EventHubGrpc.newBlockingStub(channel);
    }

    @Bean
    public EventHubGrpc.EventHubStub eventHubAsyncStub(ManagedChannel channel) {
        return EventHubGrpc.newStub(channel);
    }
}
