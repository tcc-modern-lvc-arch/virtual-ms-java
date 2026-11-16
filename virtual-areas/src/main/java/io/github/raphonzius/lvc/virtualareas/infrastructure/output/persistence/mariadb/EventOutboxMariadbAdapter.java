package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionOutbox;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc.EventHubGrpcAdapter;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc.ProtoMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MariaDB-backed transactional outbox. {@link #enqueue} joins the caller's transaction (plain
 * {@link JdbcTemplate} on the same DataSource); the relay-facing methods each run in their own short
 * transaction.
 * <p>
 * Ordering contract of {@link #claimBatch}: only the <em>head</em> of each entity's queue (the lowest-id
 * unsent row of that entity/type) is eligible. A row therefore never overtakes an earlier unsent row of the
 * same entity — not while that one backs off after a failure, is leased, or is locked by another relay.
 */
@Repository
public class EventOutboxMariadbAdapter implements TransitionOutbox {

    private static final int MAX_ERROR_LENGTH = 1000;

    /** A claimed outbox row, ready to be sent. */
    public record OutboxRow(long id, String eventId, String entityId, String eventKind, byte[] payload,
                            int attempts) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public EventOutboxMariadbAdapter(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Override
    public void enqueue(AreaTransitionEvent event) {
        Timestamp now = Timestamp.from(clock.instant());
        byte[] payload = ProtoMapper.toEventRequest(event, EventHubGrpcAdapter.SOURCE).toByteArray();
        jdbc.update("""
                        INSERT INTO event_outbox (event_id, area_id, entity_id, entity_type, event_kind, payload,
                                                  created_at, next_attempt_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(), event.areaId(), event.entityId(), event.entityType().name(),
                ProtoMapper.toEventKind(event).name(), payload, now, now);
    }

    /**
     * Claims up to {@code limit} due head-of-line rows: {@code FOR UPDATE SKIP LOCKED} in a short transaction,
     * pushing {@code next_attempt_at} out by {@code lease} so that no other relay (and not this one, on its
     * next batch) picks them up while they are being sent outside the transaction.
     */
    public List<OutboxRow> claimBatch(int limit, Instant now, Duration lease) {
        Timestamp ts = Timestamp.from(now);
        return tx.execute(_ -> {
            List<OutboxRow> rows = jdbc.query("""
                            SELECT o.id, o.event_id, o.entity_id, o.event_kind, o.payload, o.attempts
                            FROM event_outbox o
                            WHERE o.sent_at IS NULL
                              AND o.next_attempt_at <= ?
                              AND NOT EXISTS (SELECT 1 FROM event_outbox p
                                              WHERE p.entity_id = o.entity_id AND p.entity_type = o.entity_type
                                                AND p.sent_at IS NULL AND p.id < o.id)
                            ORDER BY o.id
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                            """,
                    (rs, _) -> new OutboxRow(rs.getLong("id"), rs.getString("event_id"), rs.getString("entity_id"),
                            rs.getString("event_kind"), rs.getBytes("payload"), rs.getInt("attempts")),
                    ts, limit);
            if (!rows.isEmpty()) {
                Timestamp leaseUntil = Timestamp.from(now.plus(lease));
                jdbc.batchUpdate("UPDATE event_outbox SET next_attempt_at = ? WHERE id = ?",
                        rows.stream().map(r -> new Object[]{leaseUntil, r.id()}).toList());
            }
            return rows;
        });
    }

    public void markSent(long id, Instant now) {
        jdbc.update("UPDATE event_outbox SET sent_at = ?, last_error = NULL WHERE id = ?", Timestamp.from(now), id);
    }

    public void markFailed(long id, Instant nextAttemptAt, String error) {
        String trimmed = error == null ? null
                : error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        jdbc.update("UPDATE event_outbox SET attempts = attempts + 1, next_attempt_at = ?, last_error = ? WHERE id = ?",
                Timestamp.from(nextAttemptAt), trimmed, id);
    }

    public long countPending() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE sent_at IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    /** Deletes up to {@code limit} rows sent before {@code cutoff}; returns how many were removed. */
    public int deleteSentBefore(Instant cutoff, int limit) {
        return jdbc.update("DELETE FROM event_outbox WHERE sent_at IS NOT NULL AND sent_at < ? LIMIT " + limit,
                Timestamp.from(cutoff));
    }
}
