package io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Transactional-outbox relay settings ({@code virtual-areas.outbox.*}). The poll interval, initial delay and
 * cleanup interval are read straight into {@code @Scheduled} (see {@code OutboxRelay}).
 *
 * @param batchSize      max rows claimed per batch (one head-of-line event per entity)
 * @param backoffInitial delay before the first retry of a failed send; doubles per attempt
 * @param backoffMax     ceiling of the retry delay
 * @param lease          how long a claimed row stays invisible to other relays (crash recovery)
 * @param retention      sent rows older than this are deleted by the cleanup job
 */
@ConfigurationProperties(prefix = "virtual-areas.outbox")
public record OutboxProperties(Integer batchSize,
                               Duration backoffInitial,
                               Duration backoffMax,
                               Duration lease,
                               Duration retention) {

    public OutboxProperties {
        if (batchSize == null) batchSize = 50;
        if (backoffInitial == null) backoffInitial = Duration.ofSeconds(1);
        if (backoffMax == null) backoffMax = Duration.ofMinutes(5);
        if (lease == null) lease = Duration.ofSeconds(60);
        if (retention == null) retention = Duration.ofDays(7);
        if (batchSize < 1) throw new IllegalArgumentException("virtual-areas.outbox.batch-size must be >= 1");
        if (backoffInitial.isNegative() || backoffInitial.isZero())
            throw new IllegalArgumentException("virtual-areas.outbox.backoff-initial must be positive");
        if (backoffMax.compareTo(backoffInitial) < 0)
            throw new IllegalArgumentException("virtual-areas.outbox.backoff-max < backoff-initial");
        if (lease.isNegative() || lease.isZero())
            throw new IllegalArgumentException("virtual-areas.outbox.lease must be positive");
    }
}
