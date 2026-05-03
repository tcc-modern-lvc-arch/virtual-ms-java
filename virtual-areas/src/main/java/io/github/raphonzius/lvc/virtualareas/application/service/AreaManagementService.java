package io.github.raphonzius.lvc.virtualareas.application.service;

import io.github.raphonzius.lvc.virtualareas.application.exception.AreaApplicationException;
import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaAction;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaPort;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PoiType;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterest;
import io.github.raphonzius.lvc.virtualareas.domain.poi.PointOfInterestPort;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreateAreaRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreatePoiRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AreaManagementService {

    private final AreaPort areaPort;
    private final PointOfInterestPort poiPort;

    // ── Areas ────────────────────────────────────────────────────────────────────

    @Transactional
    public Area createArea(CreateAreaRequest req) {
        Area area = buildArea(UUID.randomUUID().toString(), req, true);
        return areaPort.save(area);
    }

    @Transactional
    public Area updateArea(String id, CreateAreaRequest req) {
        areaPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "Area not found: " + id));
        Area updated = buildArea(id, req, true);
        return areaPort.save(updated);
    }

    @Transactional(readOnly = true)
    public Area getArea(String id) {
        return areaPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "Area not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Area> getAllAreas() {
        return areaPort.findAll();
    }

    @Transactional(readOnly = true)
    public List<Area> getActiveAreas() {
        return areaPort.findActive();
    }

    @Transactional
    public void deleteArea(String id) {
        areaPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "Area not found: " + id));
        areaPort.delete(id);
    }

    @Transactional
    public Area toggleActive(String id) {
        Area area = areaPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "Area not found: " + id));
        Area toggled = switch (area) {
            case Area.PolygonArea p ->
                    new Area.PolygonArea(p.id(), p.name(), p.description(), p.vertices(), p.monitoredEntityTypes(), p.action(), !p.active(), p.targetPoiId(), p.patrolZone());
            case Area.CircleArea c ->
                    new Area.CircleArea(c.id(), c.name(), c.description(), c.center(), c.radiusMeters(), c.monitoredEntityTypes(), c.action(), !c.active(), c.targetPoiId(), c.patrolZone());
            case Area.CorridorArea co ->
                    new Area.CorridorArea(co.id(), co.name(), co.description(), co.centerline(), co.halfWidthMeters(), co.monitoredEntityTypes(), co.action(), !co.active(), co.targetPoiId(), co.patrolZone());
        };
        return areaPort.save(toggled);
    }

    // ── POIs ─────────────────────────────────────────────────────────────────────

    @Transactional
    public PointOfInterest createPoi(CreatePoiRequest req) {
        var poi = new PointOfInterest(
                UUID.randomUUID().toString(),
                req.name(), req.description(),
                PoiType.valueOf(req.type()),
                new Coordinate(req.lat(), req.lon(), req.altitudeM()),
                req.metadata(), true
        );
        return poiPort.save(poi);
    }

    @Transactional
    public PointOfInterest updatePoi(String id, CreatePoiRequest req) {
        poiPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "POI not found: " + id));
        var updated = new PointOfInterest(id, req.name(), req.description(),
                PoiType.valueOf(req.type()),
                new Coordinate(req.lat(), req.lon(), req.altitudeM()),
                req.metadata(), true);
        return poiPort.save(updated);
    }

    @Transactional(readOnly = true)
    public PointOfInterest getPoi(String id) {
        return poiPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "POI not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PointOfInterest> getAllPois() {
        return poiPort.findAll();
    }

    @Transactional
    public void deletePoi(String id) {
        poiPort.findById(id).orElseThrow(() ->
                new AreaApplicationException(404, "POI not found: " + id));
        poiPort.delete(id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private Area buildArea(String id, CreateAreaRequest req, boolean active) {
        List<MonitoredEntityType> entityTypes = req.monitoredEntityTypes().stream()
                .map(MonitoredEntityType::valueOf).toList();
        AreaAction action = AreaAction.valueOf(req.action());
        List<Coordinate> coords = req.coordinates().stream()
                .map(c -> new Coordinate(c.lat(), c.lon(), c.altitudeM())).toList();

        return switch (req.areaType().toUpperCase()) {
            case "POLYGON" ->
                    new Area.PolygonArea(id, req.name(), req.description(), coords, entityTypes, action, active, req.targetPoiId(), req.patrolZone());
            case "CIRCLE" -> new Area.CircleArea(id, req.name(), req.description(), coords.get(0),
                    req.radiusMeters(), entityTypes, action, active, req.targetPoiId(), req.patrolZone());
            case "CORRIDOR" -> new Area.CorridorArea(id, req.name(), req.description(), coords,
                    req.radiusMeters(), entityTypes, action, active, req.targetPoiId(), req.patrolZone());
            default -> throw new AreaApplicationException(400, "Unknown area type: " + req.areaType());
        };
    }
}
