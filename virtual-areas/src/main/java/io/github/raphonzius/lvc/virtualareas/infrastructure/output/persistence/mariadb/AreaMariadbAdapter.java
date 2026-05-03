package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaAction;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaPort;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AreaMariadbAdapter implements AreaPort {

    private final AreaJpaRepository repository;

    @Override
    public Area save(Area area) {
        return toDomain(repository.save(toEntity(area)));
    }

    @Override
    public Optional<Area> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Area> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Area> findActive() {
        return repository.findByActiveTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Area toDomain(AreaEntity e) {
        List<MonitoredEntityType> types = e.getMonitoredEntityTypes().stream()
                .map(MonitoredEntityType::valueOf).toList();
        AreaAction action = AreaAction.valueOf(e.getAction().name());
        List<Coordinate> coords = e.getCoordinates().stream()
                .map(c -> new Coordinate(c.lat(), c.lon(), c.altitudeM())).toList();

        return switch (e.getAreaType()) {
            case POLYGON -> new Area.PolygonArea(e.getId(), e.getName(), e.getDescription(),
                    coords, types, action, e.isActive(), e.getTargetPoiId(), e.isPatrolZone());
            case CIRCLE -> new Area.CircleArea(e.getId(), e.getName(), e.getDescription(),
                    coords.get(0), e.getRadiusMeters(), types, action, e.isActive(), e.getTargetPoiId(), e.isPatrolZone());
            case CORRIDOR -> new Area.CorridorArea(e.getId(), e.getName(), e.getDescription(),
                    coords, e.getRadiusMeters(), types, action, e.isActive(), e.getTargetPoiId(), e.isPatrolZone());
        };
    }

    private AreaEntity toEntity(Area area) {
        List<String> types = area.monitoredEntityTypes().stream()
                .map(MonitoredEntityType::name).toList();
        AreaEntity.AreaActionJpa action = AreaEntity.AreaActionJpa.valueOf(area.action().name());

        return switch (area) {
            case Area.PolygonArea p -> AreaEntity.builder()
                    .id(p.id()).name(p.name()).description(p.description())
                    .areaType(AreaEntity.AreaTypeJpa.POLYGON)
                    .coordinates(toJson(p.vertices()))
                    .monitoredEntityTypes(types).action(action).active(p.active())
                    .targetPoiId(p.targetPoiId()).patrolZone(p.patrolZone())
                    .build();
            case Area.CircleArea c -> AreaEntity.builder()
                    .id(c.id()).name(c.name()).description(c.description())
                    .areaType(AreaEntity.AreaTypeJpa.CIRCLE)
                    .coordinates(List.of(new CoordinateJson(c.center().lat(), c.center().lon(), c.center().altitudeM())))
                    .radiusMeters(c.radiusMeters())
                    .monitoredEntityTypes(types).action(action).active(c.active())
                    .targetPoiId(c.targetPoiId()).patrolZone(c.patrolZone())
                    .build();
            case Area.CorridorArea co -> AreaEntity.builder()
                    .id(co.id()).name(co.name()).description(co.description())
                    .areaType(AreaEntity.AreaTypeJpa.CORRIDOR)
                    .coordinates(toJson(co.centerline()))
                    .radiusMeters(co.halfWidthMeters())
                    .monitoredEntityTypes(types).action(action).active(co.active())
                    .targetPoiId(co.targetPoiId()).patrolZone(co.patrolZone())
                    .build();
        };
    }

    private List<CoordinateJson> toJson(List<Coordinate> coords) {
        return coords.stream()
                .map(c -> new CoordinateJson(c.lat(), c.lon(), c.altitudeM()))
                .toList();
    }
}
