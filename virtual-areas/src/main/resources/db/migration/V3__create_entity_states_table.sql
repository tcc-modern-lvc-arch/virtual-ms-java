CREATE TABLE entity_states
(
    entity_id    VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(50)  NOT NULL,
    area_id      VARCHAR(36)  NOT NULL,
    entered_at   DATETIME(6),
    last_seen_at DATETIME(6),
    PRIMARY KEY (entity_id, entity_type, area_id)
);
