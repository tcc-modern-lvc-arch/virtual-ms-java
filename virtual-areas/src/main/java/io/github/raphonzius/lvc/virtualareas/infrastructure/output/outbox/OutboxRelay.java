package io.github.raphonzius.lvc.virtualareas.infrastructure.output.outbox;

import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.OutboxProperties;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc.EventRequestSender;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb.EventOutboxMariadbAdapter;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb.EventOutboxMariadbAdapter.OutboxRow;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb.OutboxEnqueued;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Delivers the transactional outbox to event-hub (at-least-once).
 * <ul>
 *   <li><b>Triggers:</b> a fixed-delay poll ({@code virtual-areas.outbox.poll-interval}, default 500ms) plus a
 *       non-blocking nudge right after the enqueuing transaction commits.</li>
 *   <li><b>Claim:</b> {@code SELECT ... FOR UPDATE SKIP LOCKED} in a short transaction; claimed rows are leased
 *       and sent <em>outside</em> any transaction, so no JDBC connection is held during gRPC calls.</li>
 *   <li><b>Ordering:</b> per entity (entity id + type). Only the oldest unsent event of each entity is
 *       claimable, so a successor can never overtake a predecessor that is failing/backing off; events of
 *       other entities keep flowing. Consequently a permanently failing head blocks only its own entity (it is
 *       retried forever with capped backoff; see {@code last_error}).</li>
 *   <li><b>Failure:</b> attempts+1, {@code next_attempt_at = now + min(max, initial * 2^(attempts-1))},
 *       {@code last_error} recorded.</li>
 *   <li><b>Cleanup:</b> sent rows older than {@code virtual-areas.outbox.retention} are deleted in chunks.</li>
 * </ul>
 */
@Component
@Slf4j
public class OutboxRelay {

    private static final int CLEANUP_CHUNK = 1000;

    private final EventOutboxMariadbAdapter store;
    private final EventRequestSender sender;
    private final OutboxProperties props;
    private final ExecutorService executor;
    private final Clock clock;
    private final Counter sentCounter;
    private final Counter failureCounter;

    private final ReentrantLock draining = new ReentrantLock();
    private final AtomicBoolean rerun = new AtomicBoolean();
    private final AtomicBoolean nudgeQueued = new AtomicBoolean();

    public OutboxRelay(EventOutboxMariadbAdapter store, EventRequestSender sender, OutboxProperties props,
                       MeterRegistry meters, @Qualifier("sideEffectExecutor") ExecutorService executor,
                       Clock clock) {
        this.store = store;
        this.sender = sender;
        this.props = props;
        this.executor = executor;
        this.clock = clock;
        this.sentCounter = Counter.builder("virtualareas.outbox.sent")
                .description("Outbox events delivered to event-hub").register(meters);
        this.failureCounter = Counter.builder("virtualareas.outbox.failures")
                .description("Failed attempts to deliver an outbox event").register(meters);
        Gauge.builder("virtualareas.outbox.pending", this, OutboxRelay::pending)
                .description("Outbox events not yet delivered").register(meters);
    }

    private double pending() {
        try {
            return store.countPending();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    // ── Triggers ────────────────────────────────────────────────────────────────

    // initial delay keeps the relay quiet during startup (e.g. AOT-cache training exits on refresh)
    @Scheduled(initialDelayString = "${virtual-areas.outbox.initial-delay:5s}",
            fixedDelayString = "${virtual-areas.outbox.poll-interval:500ms}")
    void poll() {
        try {
            drain();
        } catch (Exception e) {
            log.warn("Outbox poll failed: {}", e.toString());
        }
    }

    /** Wake-up after the transaction that enqueued an event commits. Never blocks and never throws. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onEnqueued(OutboxEnqueued ignored) {
        if (!nudgeQueued.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                nudgeQueued.set(false);
                try {
                    drain();
                } catch (Throwable t) {
                    log.warn("Outbox nudge failed: {}", t.toString());
                }
            });
        } catch (RuntimeException e) { // executor closed during shutdown: the poll will pick the row up
            nudgeQueued.set(false);
        }
    }

    @Scheduled(initialDelayString = "${virtual-areas.outbox.cleanup-initial-delay:1m}",
            fixedDelayString = "${virtual-areas.outbox.cleanup-interval:1h}")
    void cleanup() {
        try {
            Instant cutoff = clock.instant().minus(props.retention());
            int total = 0;
            int removed;
            do {
                removed = store.deleteSentBefore(cutoff, CLEANUP_CHUNK);
                total += removed;
            } while (removed == CLEANUP_CHUNK);
            if (total > 0) log.info("Outbox cleanup removed {} sent rows older than {}", total, props.retention());
        } catch (Exception e) {
            log.warn("Outbox cleanup failed: {}", e.toString());
        }
    }

    // ── Relay ───────────────────────────────────────────────────────────────────

    /**
     * Sends every due event, batch after batch, until nothing is due. Only one drain runs per instance at a
     * time (a trigger arriving meanwhile makes the running one loop again); other instances are excluded by
     * SKIP LOCKED + lease.
     *
     * @return number of events delivered
     */
    int drain() {
        if (!draining.tryLock()) {
            rerun.set(true);
            return 0;
        }
        try {
            int delivered = 0;
            do {
                rerun.set(false);
                int batchDelivered;
                do { // a delivered head may uncover the next event of its entity: claim again
                    batchDelivered = 0;
                    for (OutboxRow row : store.claimBatch(props.batchSize(), clock.instant(), props.lease())) {
                        if (deliver(row)) batchDelivered++;
                    }
                    delivered += batchDelivered;
                } while (batchDelivered > 0);
            } while (rerun.get());
            return delivered;
        } finally {
            draining.unlock();
        }
    }

    private boolean deliver(OutboxRow row) {
        try {
            sender.send(EventRequest.parseFrom(row.payload()));
            store.markSent(row.id(), clock.instant());
            sentCounter.increment();
            return true;
        } catch (Throwable t) {
            fail(row, t);
            if (t instanceof Error error) throw error;
            return false;
        }
    }

    private void fail(OutboxRow row, Throwable t) {
        int attempts = row.attempts() + 1;
        Duration delay = backoff(attempts, props.backoffInitial(), props.backoffMax());
        failureCounter.increment();
        log.warn("Outbox event {} ({} entity={}) not delivered (attempt {}), retrying in {} ms: {}",
                row.eventId(), row.eventKind(), row.entityId(), attempts, delay.toMillis(), t.toString());
        store.markFailed(row.id(), clock.instant().plus(delay), t.toString());
    }

    /** {@code min(max, initial * 2^(attempts-1))}. */
    static Duration backoff(int attempts, Duration initial, Duration max) {
        long shift = Math.min(Math.max(attempts - 1, 0), 30);
        long millis = initial.toMillis() << shift;
        return millis <= 0 || millis > max.toMillis() ? max : Duration.ofMillis(millis);
    }
}
