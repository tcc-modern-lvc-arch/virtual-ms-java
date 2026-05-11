-- Idempotent seed for points_of_interest + areas.
-- Insert POIs first so areas.target_poi_id FK is satisfied at insert time.

-- ── Points of Interest ────────────────────────────────────────────────────────

-- Caio Prado / Consolação bus stop, used as the MISSION_TRIGGER target POI.
INSERT IGNORE INTO points_of_interest (id, name, description, poi_type, latitude, longitude, altitude_m, metadata, active)
VALUES ('b2c3d4e5-0001-0000-0000-000000000000',
        'Caio Prado / Consolação',
        'Ponto de ônibus — Caio Prado com Consolação',
        'MISSION_TARGET',
        -23.5482453,
        -46.6520375,
        NULL,
        NULL,
        TRUE);

-- ── Areas ─────────────────────────────────────────────────────────────────────

-- Mackenzie Campus — POLYGON, DRONE monitoring, drone patrol boundary
-- (simplified OSM 38629632)
INSERT IGNORE INTO areas (id, name, description, area_type, coordinates, radius_meters,
                          monitored_entity_types, action, target_poi_id, patrol_zone, active)
VALUES ('a1b2c3d4-0001-0000-0000-000000000000',
        'Mackenzie Campus',
        'Universidade Presbiteriana Mackenzie — Rua da Consolação 896, São Paulo',
        'POLYGON',
        '[{"lat":-23.5433,"lon":-46.6524,"altitude_m":null},{"lat":-23.5430,"lon":-46.6490,"altitude_m":null},{"lat":-23.5443,"lon":-46.6471,"altitude_m":null},{"lat":-23.5462,"lon":-46.6472,"altitude_m":null},{"lat":-23.5476,"lon":-46.6498,"altitude_m":null},{"lat":-23.5470,"lon":-46.6521,"altitude_m":null},{"lat":-23.5453,"lon":-46.6528,"altitude_m":null},{"lat":-23.5433,"lon":-46.6524,"altitude_m":null}]',
        NULL,
        '["DRONE"]',
        'CHECKIN_CHECKOUT',
        NULL,
        TRUE,
        TRUE);

-- Rua da Consolação — CORRIDOR 20m half-width, BUS monitoring
-- (simplified OSM 333172735)
INSERT IGNORE INTO areas (id, name, description, area_type, coordinates, radius_meters,
                          monitored_entity_types, action, target_poi_id, patrol_zone, active)
VALUES ('a1b2c3d4-0002-0000-0000-000000000000',
        'Rua da Consolação',
        'Corredor viário — Av. Paulista até Largo do Arouche',
        'CORRIDOR',
        '[{"lat":-23.5390,"lon":-46.6471,"altitude_m":null},{"lat":-23.5413,"lon":-46.6481,"altitude_m":null},{"lat":-23.5440,"lon":-46.6495,"altitude_m":null},{"lat":-23.5465,"lon":-46.6503,"altitude_m":null},{"lat":-23.5493,"lon":-46.6514,"altitude_m":null},{"lat":-23.5520,"lon":-46.6526,"altitude_m":null}]',
        20.0,
        '["BUS"]',
        'CHECKIN_CHECKOUT',
        NULL,
        FALSE,
        TRUE);

-- Consolação Neighborhood — POLYGON, BUS monitoring (simplified OSM 2731076)
INSERT IGNORE INTO areas (id, name, description, area_type, coordinates, radius_meters,
                          monitored_entity_types, action, target_poi_id, patrol_zone, active)
VALUES ('a1b2c3d4-0003-0000-0000-000000000000',
        'Consolação Neighborhood',
        'Bairro da Consolação — São Paulo',
        'POLYGON',
        '[{"lat":-23.5340,"lon":-46.6560,"altitude_m":null},{"lat":-23.5340,"lon":-46.6440,"altitude_m":null},{"lat":-23.5420,"lon":-46.6400,"altitude_m":null},{"lat":-23.5520,"lon":-46.6400,"altitude_m":null},{"lat":-23.5590,"lon":-46.6480,"altitude_m":null},{"lat":-23.5590,"lon":-46.6560,"altitude_m":null},{"lat":-23.5500,"lon":-46.6600,"altitude_m":null},{"lat":-23.5340,"lon":-46.6560,"altitude_m":null}]',
        NULL,
        '["BUS"]',
        'CHECKIN_CHECKOUT',
        NULL,
        FALSE,
        TRUE);

-- Caio Prado C/B Stop Area — CIRCLE 80m, BUS MISSION_TRIGGER → Caio Prado POI
-- Coordinates from OpenStreetMap (Rua Caio Prado bus stop, Consolação).
INSERT IGNORE INTO areas (id, name, description, area_type, coordinates, radius_meters,
                          monitored_entity_types, action, target_poi_id, patrol_zone, active)
VALUES ('a1b2c3d4-0004-0000-0000-000000000000',
        'Caio Prado C/B Stop Area',
        'Área de parada do ônibus Caio Prado — raio 80m',
        'CIRCLE',
        '[{"lat":-23.5482453,"lon":-46.6520375,"altitude_m":null}]',
        80.0,
        '["BUS"]',
        'MISSION_TRIGGER',
        'b2c3d4e5-0001-0000-0000-000000000000',
        FALSE,
        TRUE);

-- Santos Port — CIRCLE 5000m, VESSEL monitoring
INSERT IGNORE INTO areas (id, name, description, area_type, coordinates, radius_meters,
                          monitored_entity_types, action, target_poi_id, patrol_zone, active)
VALUES ('a1b2c3d4-0005-0000-0000-000000000000',
        'Santos Port',
        'Porto de Santos — principal porto do Brasil',
        'CIRCLE',
        '[{"lat":-23.9553,"lon":-46.3167,"altitude_m":null}]',
        5000.0,
        '["VESSEL"]',
        'CHECKIN_CHECKOUT',
        NULL,
        FALSE,
        TRUE);
