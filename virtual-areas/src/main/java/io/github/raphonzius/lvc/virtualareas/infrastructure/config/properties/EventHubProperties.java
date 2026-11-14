package io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * event-hub client settings. The network address itself lives in
 * {@code spring.grpc.client.channel.<channel>.target} (Spring gRPC channel factory).
 *
 * @param channel                 name of the Spring gRPC channel to use
 * @param deadline                per-call deadline for unary SendEvent calls
 * @param reconnectInitialBackoff first delay before re-subscribing after a stream failure
 * @param reconnectMaxBackoff     upper bound of the exponential re-subscribe backoff
 * @param reconnectResetAfter     the backoff resets only if a stream lived at least this long
 */
@ConfigurationProperties(prefix = "eventhub.grpc")
public record EventHubProperties(String channel,
                                 Duration deadline,
                                 Duration reconnectInitialBackoff,
                                 Duration reconnectMaxBackoff,
                                 Duration reconnectResetAfter) {

    public EventHubProperties {
        if (channel == null || channel.isBlank()) channel = "event-hub";
        if (reconnectResetAfter == null) reconnectResetAfter = Duration.ofSeconds(30);
        if (deadline == null) deadline = Duration.ofSeconds(2);
        if (reconnectInitialBackoff == null) reconnectInitialBackoff = Duration.ofSeconds(1);
        if (reconnectMaxBackoff == null) reconnectMaxBackoff = Duration.ofSeconds(30);
        if (deadline.isNegative() || deadline.isZero())
            throw new IllegalArgumentException("eventhub.grpc.deadline must be positive");
        if (reconnectMaxBackoff.compareTo(reconnectInitialBackoff) < 0)
            throw new IllegalArgumentException("eventhub.grpc.reconnect-max-backoff < reconnect-initial-backoff");
    }
}
