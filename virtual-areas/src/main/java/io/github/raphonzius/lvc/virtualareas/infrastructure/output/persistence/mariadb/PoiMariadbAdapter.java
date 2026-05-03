package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PoiType;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterest;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PoiMariadbAdapter implements PointOfInterestPort {

    private final PoiJpaRepository repository;

    @Override
    public PointOfInterest save(PointOfInterest poi) {
        return toDomain(repository.save(toEntity(poi)));
    }

    @Override
    public Optional<PointOfInterest> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public List<PointOfInterest> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<PointOfInterest> findActive() {
        return repository.findByActiveTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    private PointOfInterest toDomain(PoiEntity e) {
        return new PointOfInterest(
                e.getId(), e.getName(), e.getDescription(),
                PoiType.valueOf(e.getPoiType().name()),
                new Coordinate(e.getLatitude(), e.getLongitude(), e.getAltitudeM()),
                e.getMetadata(), e.isActive()
        );
    }

    private PoiEntity toEntity(PointOfInterest p) {
        return PoiEntity.builder()
                .id(p.id()).name(p.name()).description(p.description())
                .poiType(PoiEntity.PoiTypeJpa.valueOf(p.type().name()))
                .latitude(p.coordinate().lat())
                .longitude(p.coordinate().lon())
                .altitudeM(p.coordinate().altitudeM())
                .metadata(p.metadata())
                .active(p.active())
                .build();
    }
}
