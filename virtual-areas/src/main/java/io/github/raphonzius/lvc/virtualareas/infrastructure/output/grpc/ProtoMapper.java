package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.BusPayload;
import io.github.raphonzius.lvc.proto.event.DronePayload;
import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.proto.event.VesselPayload;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.MissionTarget;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;

import java.time.Instant;
import java.util.Optional;

/**
 * Anti-corruption layer between the event-hub protobuf contract and the domain model.
 * <p>
 * Every proto enum has an implicit {@code UNRECOGNIZED} constant (a newer peer may send values this
 * build does not know). Mapping proto → domain therefore returns {@link Optional#empty()} for unknown
 * values instead of silently defaulting to an arbitrary constant.
 */
public final class ProtoMapper {

    private ProtoMapper() {
    }

    // ── Enums ────────────────────────────────────────────────────────────────

    public static EntityType toProto(MonitoredEntityType type) {
        return switch (type) {
            case DRONE -> EntityType.DRONE;
            case BUS -> EntityType.BUS;
            case VESSEL -> EntityType.VESSEL;
            case FLOOD_AREA -> EntityType.FLOOD_AREA;
            case RAIN_AREA -> EntityType.RAIN_AREA;
        };
    }

    public static Optional<MonitoredEntityType> fromProto(EntityType type) {
        return switch (type) {
            case DRONE -> Optional.of(MonitoredEntityType.DRONE);
            case BUS -> Optional.of(MonitoredEntityType.BUS);
            case VESSEL -> Optional.of(MonitoredEntityType.VESSEL);
            case FLOOD_AREA -> Optional.of(MonitoredEntityType.FLOOD_AREA);
            case RAIN_AREA -> Optional.of(MonitoredEntityType.RAIN_AREA);
            case UNRECOGNIZED -> Optional.empty();
        };
    }

    public static io.github.raphonzius.lvc.proto.event.LvcOrigin toProto(LvcOrigin lvc) {
        return switch (lvc) {
            case LIVE -> io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE;
            case VIRTUAL -> io.github.raphonzius.lvc.proto.event.LvcOrigin.VIRTUAL;
            case CONSTRUCTIVE -> io.github.raphonzius.lvc.proto.event.LvcOrigin.CONSTRUCTIVE;
        };
    }

    public static Optional<LvcOrigin> fromProto(io.github.raphonzius.lvc.proto.event.LvcOrigin lvc) {
        return switch (lvc) {
            case LIVE -> Optional.of(LvcOrigin.LIVE);
            case VIRTUAL -> Optional.of(LvcOrigin.VIRTUAL);
            case CONSTRUCTIVE -> Optional.of(LvcOrigin.CONSTRUCTIVE);
            case UNRECOGNIZED -> Optional.empty();
        };
    }

    public static EventKind toEventKind(AreaTransitionEvent event) {
        return switch (event) {
            case AreaTransitionEvent.Checkin _ -> EventKind.CHECKIN;
            case AreaTransitionEvent.Checkout _ -> EventKind.CHECKOUT;
        };
    }

    // ── Geometry ─────────────────────────────────────────────────────────────

    public static Location toLocation(Coordinate c) {
        Location.Builder b = Location.newBuilder().setLat(c.lat()).setLon(c.lon());
        if (c.altitudeM() != null) b.setAltitudeM(c.altitudeM());
        return b.build();
    }

    public static Coordinate toCoordinate(Location loc) {
        return new Coordinate(loc.getLat(), loc.getLon(), loc.hasAltitudeM() ? loc.getAltitudeM() : null);
    }

    /** Position carried by a moving-entity payload (drone/bus/vessel); empty for area payloads or none. */
    public static Optional<Coordinate> locationOf(EventRequest event) {
        return switch (event.getPayloadCase()) {
            case DRONE -> Optional.of(toCoordinate(event.getDrone().getLocation()));
            case BUS -> Optional.of(toCoordinate(event.getBus().getLocation()));
            case VESSEL -> Optional.of(toCoordinate(event.getVessel().getLocation()));
            case FLOOD_AREA, RAIN_AREA, PAYLOAD_NOT_SET -> Optional.empty();
        };
    }

    // ── Events ───────────────────────────────────────────────────────────────

    public static EventRequest toEventRequest(AreaTransitionEvent event, String source) {
        EventRequest.Builder b = envelope(event.areaId(), source, toEventKind(event), event.entityType(),
                event.entityId(), event.position(), event.lvc(), event.timestamp());
        if (event instanceof AreaTransitionEvent.Checkin checkin && checkin.missionTarget() != null) {
            b.setMissionTarget(toProto(checkin.missionTarget()));
        }
        return b.build();
    }

    public static EventRequest moveRequest(String areaId, String source, String entityId,
                                           MonitoredEntityType entityType, Coordinate position,
                                           LvcOrigin lvc, Instant timestamp) {
        return envelope(areaId, source, EventKind.MOVE, entityType, entityId, position, lvc, timestamp).build();
    }

    static io.github.raphonzius.lvc.proto.event.MissionTarget toProto(MissionTarget target) {
        Coordinate c = target.coordinate();
        var b = io.github.raphonzius.lvc.proto.event.MissionTarget.newBuilder()
                .setLat(c.lat())
                .setLon(c.lon());
        if (c.altitudeM() != null) b.setAltitudeM(c.altitudeM());
        if (target.poiName() != null) b.setPoiName(target.poiName());
        if (target.poiId() != null) b.setPoiId(target.poiId());
        return b.build();
    }

    private static EventRequest.Builder envelope(String areaId, String source, EventKind kind,
                                                 MonitoredEntityType entityType, String entityId,
                                                 Coordinate position, LvcOrigin lvc, Instant timestamp) {
        EventRequest.Builder b = EventRequest.newBuilder()
                .setAreaId(areaId)
                .setSource(source)
                .setEventKind(kind)
                .setEntityType(toProto(entityType))
                .setLvc(toProto(lvc))
                .setTimestampMs(timestamp.toEpochMilli())
                .setEntityId(entityId);
        Location location = toLocation(position);
        switch (entityType) {
            case DRONE -> b.setDrone(DronePayload.newBuilder().setLocation(location));
            case BUS -> b.setBus(BusPayload.newBuilder().setLocation(location));
            case VESSEL -> b.setVessel(VesselPayload.newBuilder().setLocation(location));
            case FLOOD_AREA, RAIN_AREA -> {
                // area payloads carry centroid/polygon and are built by their own use cases
            }
        }
        return b;
    }
}
