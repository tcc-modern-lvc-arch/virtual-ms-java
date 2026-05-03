CREATE TABLE areas
(
    id                     VARCHAR(36)  NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    description            VARCHAR(255),
    area_type              ENUM('POLYGON','CIRCLE','CORRIDOR') NOT NULL,
    coordinates            JSON         NOT NULL,
    radius_meters DOUBLE,
    monitored_entity_types JSON         NOT NULL,
    action                 ENUM('CHECKIN_CHECKOUT','MISSION_TRIGGER') NOT NULL,
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
