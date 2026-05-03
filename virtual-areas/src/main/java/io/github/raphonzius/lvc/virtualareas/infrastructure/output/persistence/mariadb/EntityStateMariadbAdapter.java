package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityState;
import io.github.raphonzius.lvc.virtualareas.domain.entity.EntityStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EntityStateMariadbAdapter implements EntityStatePort {

    private final JdbcTemplate jdbc;

    @Override
    public void upsert(EntityState state) {
        jdbc.update("""
                        INSERT INTO entity_states (entity_id, entity_type, area_id, entered_at, last_seen_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE last_seen_at = VALUES(last_seen_at),
                                                entered_at   = IF(entered_at IS NULL, VALUES(entered_at), entered_at)
                        """,
                state.entityId(), state.entityType().name(), state.areaId(),
                Timestamp.from(state.enteredAt()), Timestamp.from(state.lastSeenAt())
        );
    }

    @Override
    public void remove(String entityId, MonitoredEntityType entityType, String areaId) {
        jdbc.update(
                "DELETE FROM entity_states WHERE entity_id = ? AND entity_type = ? AND area_id = ?",
                entityId, entityType.name(), areaId
        );
    }

    @Override
    public List<EntityState> findCurrentAreasFor(String entityId, MonitoredEntityType entityType) {
        return jdbc.query(
                "SELECT * FROM entity_states WHERE entity_id = ? AND entity_type = ?",
                (rs, _) -> new EntityState(
                        rs.getString("entity_id"),
                        MonitoredEntityType.valueOf(rs.getString("entity_type")),
                        rs.getString("area_id"),
                        rs.getTimestamp("entered_at").toInstant(),
                        rs.getTimestamp("last_seen_at").toInstant()
                ),
                entityId, entityType.name()
        );
    }

    @Override
    public List<EntityState> findEntitiesInArea(String areaId) {
        return jdbc.query(
                "SELECT * FROM entity_states WHERE area_id = ?",
                (rs, _) -> new EntityState(
                        rs.getString("entity_id"),
                        MonitoredEntityType.valueOf(rs.getString("entity_type")),
                        rs.getString("area_id"),
                        rs.getTimestamp("entered_at").toInstant(),
                        rs.getTimestamp("last_seen_at").toInstant()
                ),
                areaId
        );
    }

    @Override
    public List<EntityState> findAll() {
        return jdbc.query(
                "SELECT * FROM entity_states ORDER BY last_seen_at DESC",
                (rs, _) -> new EntityState(
                        rs.getString("entity_id"),
                        MonitoredEntityType.valueOf(rs.getString("entity_type")),
                        rs.getString("area_id"),
                        rs.getTimestamp("entered_at").toInstant(),
                        rs.getTimestamp("last_seen_at").toInstant()
                )
        );
    }
}
