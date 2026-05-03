package io.github.raphonzius.lvc.virtualareas.domain.entity;

import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;

import java.util.List;

public interface EntityStatePort {
    void upsert(EntityState state);

    void remove(String entityId, MonitoredEntityType entityType, String areaId);

    List<EntityState> findCurrentAreasFor(String entityId, MonitoredEntityType entityType);

    List<EntityState> findEntitiesInArea(String areaId);

    List<EntityState> findAll();
}
