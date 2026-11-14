-- Transactional outbox for CHECKIN / CHECKOUT events: written in the same transaction as the
-- entity_states change, delivered to event-hub by OutboxRelay (at-least-once, ordered per entity).
CREATE TABLE IF NOT EXISTS event_outbox
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        CHAR(36)     NOT NULL,
    area_id         VARCHAR(36)  NOT NULL,
    entity_id       VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    event_kind      VARCHAR(20)  NOT NULL,
    payload         LONGBLOB     NOT NULL,                -- serialized event.EventRequest (protobuf)
    created_at      DATETIME(6)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)  NOT NULL,
    last_error      VARCHAR(1000) NULL,
    sent_at         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_event_outbox_event_id (event_id),
    KEY idx_event_outbox_pending (sent_at, next_attempt_at),
    -- "is there an earlier unsent event for this entity?" (per-entity ordering in the relay)
    KEY idx_event_outbox_entity (entity_id, entity_type, sent_at, id)
);
