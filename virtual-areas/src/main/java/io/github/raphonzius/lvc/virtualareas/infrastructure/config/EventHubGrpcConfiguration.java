package io.github.raphonzius.lvc.virtualareas.infrastructure.config;

import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.EventHubProperties;
import io.grpc.ManagedChannel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * event-hub client wiring. The channel comes from Spring gRPC's {@link GrpcChannelFactory}, so target,
 * keepalive, TLS, service config (retry) etc. are configured under
 * {@code spring.grpc.client.channel.event-hub.*} and the factory owns the channel's shutdown.
 */
@Configuration
@EnableConfigurationProperties(EventHubProperties.class)
public class EventHubGrpcConfiguration {

    // destroyMethod = "": the channel factory already shuts the channel down gracefully
    @Bean(destroyMethod = "")
    public ManagedChannel eventHubChannel(GrpcChannelFactory channels, EventHubProperties props) {
        return channels.createChannel(props.channel());
    }

    @Bean
    public EventHubGrpc.EventHubBlockingStub eventHubBlockingStub(ManagedChannel eventHubChannel) {
        return EventHubGrpc.newBlockingStub(eventHubChannel);
    }

    @Bean
    public EventHubGrpc.EventHubStub eventHubAsyncStub(ManagedChannel eventHubChannel) {
        return EventHubGrpc.newStub(eventHubChannel);
    }
}
