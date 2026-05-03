CREATE TABLE points_of_interest
(
    id          VARCHAR(36)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    poi_type    ENUM('BUS_STOP','MISSION_TARGET','GENERIC') NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    altitude_m DOUBLE,
    metadata    JSON,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
