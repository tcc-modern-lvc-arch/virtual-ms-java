-- target_poi_id: links MISSION_TRIGGER areas to a target POI coordinate
ALTER TABLE areas
    ADD COLUMN target_poi_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_area_target_poi
        FOREIGN KEY (target_poi_id) REFERENCES points_of_interest(id) ON
DELETE
SET NULL;

-- patrol_zone: independent of action — an area can fire CHECKIN/CHECKOUT AND serve as a patrol boundary
ALTER TABLE areas
    ADD COLUMN patrol_zone BOOLEAN NOT NULL DEFAULT FALSE;

-- Caio Prado C/B Stop Area → Caio Prado / Consolação POI
UPDATE areas
SET target_poi_id = 'b2c3d4e5-0001-0000-0000-000000000000'
WHERE id = 'a1b2c3d4-0004-0000-0000-000000000000';

-- Mackenzie Campus: stays CHECKIN_CHECKOUT + becomes the drone patrol boundary
UPDATE areas
SET patrol_zone = TRUE
WHERE id = 'a1b2c3d4-0001-0000-0000-000000000000';

-- Caio Prado / Consolação POI → MISSION_TARGET type
UPDATE points_of_interest
SET poi_type = 'MISSION_TARGET'
WHERE id = 'b2c3d4e5-0001-0000-0000-000000000000';
