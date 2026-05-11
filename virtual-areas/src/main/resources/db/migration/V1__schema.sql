-- Idempotent DDL for the virtual_areas registry.
-- Order: points_of_interest first (areas.target_poi_id FK depends on it).

CREATE TABLE IF NOT EXISTS points_of_interest
(
    id          VARCHAR(36)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    poi_type    ENUM('BUS_STOP','MISSION_TARGET','GENERIC') NOT NULL,
    latitude    DOUBLE       NOT NULL,
    longitude   DOUBLE       NOT NULL,
    altitude_m  DOUBLE,
    metadata    JSON,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS areas
(
    id                     VARCHAR(36)  NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    description            VARCHAR(255),
    area_type              ENUM('POLYGON','CIRCLE','CORRIDOR') NOT NULL,
    coordinates            JSON         NOT NULL,
    radius_meters          DOUBLE,
    monitored_entity_types JSON         NOT NULL,
    action                 ENUM('CHECKIN_CHECKOUT','MISSION_TRIGGER') NOT NULL,
    target_poi_id          VARCHAR(36)  NULL,
    patrol_zone            BOOLEAN      NOT NULL DEFAULT FALSE,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_area_target_poi
        FOREIGN KEY (target_poi_id) REFERENCES points_of_interest(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS entity_states
(
    entity_id    VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(50)  NOT NULL,
    area_id      VARCHAR(36)  NOT NULL,
    entered_at   DATETIME(6),
    last_seen_at DATETIME(6),
    PRIMARY KEY (entity_id, entity_type, area_id)
);
