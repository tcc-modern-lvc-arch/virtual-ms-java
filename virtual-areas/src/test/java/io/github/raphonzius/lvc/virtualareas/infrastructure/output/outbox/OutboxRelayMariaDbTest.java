package io.github.raphonzius.lvc.virtualareas.infrastructure.output.outbox;

import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.virtualareas.application.service.GeofenceMonitoringService;
import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaAction;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaPort;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.InfluxProjectionPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.TransitionRecord;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.OutboxProperties;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb.EntityStateMariadbAdapter;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb.EventOutboxMariadbAdapter;
import io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb.OutboxEnqueued;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Outbox + relay against a real MariaDB (the SQL relies on FOR UPDATE SKIP LOCKED). */
@Testcontainers(disabledWithoutDocker = true)
class OutboxRelayMariaDbTest {

    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");
    private static final Coordinate CENTER = new Coordinate(-23.5466, -46.6520);
    private static final Area.CircleArea AREA = new Area.CircleArea("area-1", "Stop", "", CENTER, 80,
            List.of(MonitoredEntityType.BUS), AreaAction.CHECKIN_CHECKOUT, true, null, false);

    static MariaDBContainer mariadb = new MariaDBContainer("mariadb:11");
    static DriverManagerDataSource dataSource;

    /** Test clock the relay and the store read from. */
    static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(T0);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private final MutableClock clock = new MutableClock();
    private final List<Object> published = new CopyOnWriteArrayList<>();
    private final List<EventRequest> sent = new CopyOnWriteArrayList<>();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AreaPort areaPort = mock(AreaPort.class);
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private EventOutboxMariadbAdapter store;
    private GeofenceMonitoringService service;
    private ExecutorService executor;
    private OutboxRelay relay;
    private volatile java.util.function.Predicate<EventRequest> failing = r -> false;
    private volatile RuntimeException failure = new IllegalStateException("event-hub down");

    @BeforeAll
    static void startDb() {
        mariadb.start();
        dataSource = new DriverManagerDataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @AfterAll
    static void stopDb() {
        mariadb.stop();
    }

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM event_outbox");
        jdbc.update("DELETE FROM entity_states");
        var txManager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txManager);
        store = new EventOutboxMariadbAdapter(jdbc, txManager, published::add, clock);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        when(areaPort.findActive()).thenReturn(List.of(AREA));
        InfluxProjectionPort influx = new InfluxProjectionPort() {
            @Override
            public void writeTransition(AreaTransitionEvent event) {
            }

            @Override
            public List<TransitionRecord> queryRecent(int limit) {
                return List.of();
            }
        };
        service = new GeofenceMonitoringService(areaPort, new EntityStateMariadbAdapter(jdbc), store, influx,
                mock(PointOfInterestPort.class), executor, Duration.ofSeconds(2), meters);
        relay = newRelay(new OutboxProperties(10, Duration.ofSeconds(1), Duration.ofSeconds(8),
                Duration.ofSeconds(60), Duration.ofDays(7)));
    }

    private OutboxRelay newRelay(OutboxProperties props) {
        return new OutboxRelay(store, request -> {
            if (failing.test(request)) throw failure;
            sent.add(request);
        }, props, meters, executor, clock);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    private void move(String entity, Coordinate position) {
        tx.executeWithoutResult(_ -> service.processMove(entity, MonitoredEntityType.BUS, position,
                LvcOrigin.LIVE, clock.instant()));
    }

    private static final Coordinate FAR = new Coordinate(-23.0, -46.0);

    private long count(String where) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE " + where, Long.class);
    }

    private Instant instant(String column, long id) {
        Timestamp ts = jdbc.queryForObject("SELECT " + column + " FROM event_outbox WHERE id = ?", Timestamp.class, id);
        return ts == null ? null : ts.toInstant();
    }

    private long firstId(String entity) {
        return jdbc.queryForObject("SELECT MIN(id) FROM event_outbox WHERE entity_id = ?", Long.class, entity);
    }

    // ── transactional write ─────────────────────────────────────────────────────

    @Test
    void committedTransitionWritesEntityStateAndOutboxRowWithSerializedEventRequest() throws Exception {
        move("bus-1", CENTER);

        assertThat(count("1=1")).isEqualTo(1);
        var row = jdbc.queryForMap("SELECT * FROM event_outbox");
        assertThat(row.get("event_kind")).isEqualTo("CHECKIN");
        assertThat(row.get("area_id")).isEqualTo("area-1");
        assertThat(row.get("entity_id")).isEqualTo("bus-1");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("sent_at")).isNull();
        assertThat(row.get("event_id").toString()).hasSize(36);
        var request = EventRequest.parseFrom((byte[]) row.get("payload"));
        assertThat(request.getEventKind()).isEqualTo(EventKind.CHECKIN);
        assertThat(request.getEntityId()).isEqualTo("bus-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_states", Long.class)).isEqualTo(1);
        assertThat(published).singleElement().isInstanceOf(OutboxEnqueued.class);
    }

    @Test
    void rollbackLeavesNeitherOutboxRowNorEntityState() {
        assertThatThrownBy(() -> tx.executeWithoutResult(_ -> {
            service.processMove("bus-1", MonitoredEntityType.BUS, CENTER, LvcOrigin.LIVE, clock.instant());
            throw new IllegalStateException("boom after the move");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("1=1")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_states", Long.class)).isZero();
    }

    // ── relay ───────────────────────────────────────────────────────────────────

    @Test
    void relaySendsAndMarksRowSentAndCountsIt() {
        move("bus-1", CENTER);

        relay.drain();

        assertThat(sent).singleElement().satisfies(r -> assertThat(r.getEventKind()).isEqualTo(EventKind.CHECKIN));
        assertThat(count("sent_at IS NOT NULL")).isEqualTo(1);
        assertThat(meters.get("virtualareas.outbox.sent").counter().count()).isEqualTo(1);
        assertThat(meters.get("virtualareas.outbox.pending").gauge().value()).isZero();
    }

    @Test
    void failedSendIsRescheduledWithExponentialBackoffCappedAtMax() {
        move("bus-1", CENTER);
        long id = firstId("bus-1");
        failing = _ -> true;

        relay.drain();
        assertThat(count("attempts = 1 AND sent_at IS NULL")).isEqualTo(1);
        assertThat(instant("next_attempt_at", id)).isEqualTo(T0.plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT last_error FROM event_outbox", String.class))
                .contains("event-hub down");
        assertThat(meters.get("virtualareas.outbox.failures").counter().count()).isEqualTo(1);
        assertThat(meters.get("virtualareas.outbox.pending").gauge().value()).isEqualTo(1);

        relay.drain(); // not due yet
        assertThat(count("attempts = 1")).isEqualTo(1);

        clock.now.set(T0.plusSeconds(1));
        relay.drain();
        assertThat(instant("next_attempt_at", id)).isEqualTo(T0.plusSeconds(1 + 2));

        for (int i = 0; i < 6; i++) { // 4s, 8s, 8s ... capped at backoff-max
            clock.now.set(instant("next_attempt_at", id));
            relay.drain();
        }
        assertThat(Duration.between(clock.now.get(), instant("next_attempt_at", id))).isEqualTo(Duration.ofSeconds(8));
        assertThat(sent).isEmpty();

        failing = _ -> false; // hub is back
        clock.now.set(instant("next_attempt_at", id));
        relay.drain();
        assertThat(sent).hasSize(1);
        assertThat(count("sent_at IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void eventsOfOneEntityAreSentInOrderAndBlockedBehindAFailingHead() {
        move("bus-A", CENTER);          // A1 CHECKIN
        move("bus-B", CENTER);          // B1 CHECKIN
        move("bus-A", FAR);             // A2 CHECKOUT
        failing = r -> r.getEntityId().equals("bus-A");

        relay.drain();

        assertThat(sent).extracting(EventRequest::getEntityId).containsExactly("bus-B");
        assertThat(count("entity_id = 'bus-A' AND sent_at IS NULL")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox WHERE entity_id='bus-A' ORDER BY id LIMIT 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox WHERE entity_id='bus-A' ORDER BY id DESC LIMIT 1",
                Integer.class)).as("A2 untouched while A1 is failing").isZero();

        failing = _ -> false;
        clock.now.set(T0.plusSeconds(1));
        relay.drain();

        assertThat(sent).extracting(r -> r.getEntityId() + ":" + r.getEventKind())
                .containsExactly("bus-B:CHECKIN", "bus-A:CHECKIN", "bus-A:CHECKOUT");
    }

    @Test
    void rowsLockedByAnotherRelayAreSkipped() {
        move("bus-A", CENTER);
        move("bus-B", CENTER);
        long a = firstId("bus-A");

        // another relay instance holds the lock on A's row inside an open transaction
        tx.executeWithoutResult(_ -> {
            jdbc.queryForObject("SELECT id FROM event_outbox WHERE id = ? FOR UPDATE", Long.class, a);
            var claimedElsewhere = Executors.newVirtualThreadPerTaskExecutor();
            try {
                var rows = claimedElsewhere.submit(() -> store.claimBatch(10, clock.instant(), Duration.ofSeconds(60)))
                        .get(5, TimeUnit.SECONDS);
                assertThat(rows).extracting(EventOutboxMariadbAdapter.OutboxRow::entityId).containsExactly("bus-B");
            } catch (Exception e) {
                throw new AssertionError(e);
            } finally {
                claimedElsewhere.close();
            }
        });
    }

    @Test
    void claimedRowsAreLeasedUntilTheLeaseExpires() {
        move("bus-A", CENTER);

        assertThat(store.claimBatch(10, clock.instant(), Duration.ofSeconds(60))).hasSize(1);
        assertThat(store.claimBatch(10, clock.instant(), Duration.ofSeconds(60))).as("leased").isEmpty();
        assertThat(store.claimBatch(10, T0.plusSeconds(61), Duration.ofSeconds(60))).as("lease expired").hasSize(1);
    }

    @Test
    void unparseablePayloadIsRecordedAsFailureNotLost() {
        jdbc.update("""
                INSERT INTO event_outbox (event_id, area_id, entity_id, entity_type, event_kind, payload,
                                          created_at, next_attempt_at)
                VALUES (UUID(), 'a', 'bus-X', 'BUS', 'CHECKIN', ?, ?, ?)""",
                new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff}, Timestamp.from(T0), Timestamp.from(T0));

        relay.drain();

        assertThat(count("attempts = 1 AND sent_at IS NULL AND last_error IS NOT NULL")).isEqualTo(1);
        assertThat(sent).isEmpty();
    }

    @Test
    void errorFromSenderIsRecordedThenRethrown() {
        move("bus-1", CENTER);
        relay = new OutboxRelay(store, _ -> {
            throw new NoSuchMethodError("grpc skew");
        }, new OutboxProperties(null, null, null, null, null), meters, executor, clock);

        assertThatThrownBy(relay::drain).isInstanceOf(NoSuchMethodError.class);

        assertThat(count("attempts = 1 AND last_error LIKE '%grpc skew%'")).isEqualTo(1);
    }

    @Test
    void nudgeAfterCommitDrainsWithoutWaitingForThePoll() throws Exception {
        move("bus-1", CENTER);

        relay.onEnqueued(new OutboxEnqueued());

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (sent.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(sent).hasSize(1);
    }

    @Test
    void cleanupRemovesOnlySentRowsOlderThanRetention() {
        move("bus-old", CENTER);
        move("bus-new", CENTER);
        move("bus-pending", CENTER);
        store.markSent(firstId("bus-old"), T0.minus(Duration.ofDays(8)));
        store.markSent(firstId("bus-new"), T0.minus(Duration.ofDays(1)));

        relay.cleanup();

        assertThat(jdbc.queryForList("SELECT entity_id FROM event_outbox ORDER BY id", String.class))
                .containsExactly("bus-new", "bus-pending");
    }

    @Test
    void largeBacklogIsDrainedInSeveralBatches() {
        for (int i = 0; i < 25; i++) move("bus-" + i, CENTER);

        relay.drain(); // batch-size = 10, one head per entity per batch

        assertThat(sent).hasSize(25);
        assertThat(count("sent_at IS NULL")).isZero();
        assertThat(new ArrayList<>(sent)).extracting(EventRequest::getEntityId).doesNotHaveDuplicates();
    }
}
