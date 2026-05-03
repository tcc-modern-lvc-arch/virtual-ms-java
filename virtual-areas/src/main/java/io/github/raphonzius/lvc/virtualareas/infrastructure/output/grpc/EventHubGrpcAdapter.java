package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.*;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.EventDispatchPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.SimulationEventPort;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.infrastructure.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventHubGrpcAdapter implements EventDispatchPort, SimulationEventPort {

    private final EventHubGrpc.EventHubBlockingStub stub;

    // ── EventDispatchPort — CHECKIN / CHECKOUT ─────────────────────────────────

    @Override
    public void dispatch(AreaTransitionEvent event) {
        EventKind kind = switch (event) {
            case AreaTransitionEvent.Checkin ignored -> EventKind.CHECKIN;
            case AreaTransitionEvent.Checkout ignored -> EventKind.CHECKOUT;
        };

        EventRequest.Builder builder = EventRequest.newBuilder()
                .setAreaId(event.areaId())
                .setSource("virtual-ms-java")
                .setEventKind(kind)
                .setEntityType(toProtoEntityType(event.entityType()))
                .setLvc(event.lvc().toProto())
                .setTimestampMs(event.timestamp().toEpochMilli())
                .setEntityId(event.entityId())
                .mergeFrom(buildPayload(event.entityType(), event.position()));

        if (event instanceof AreaTransitionEvent.Checkin checkin && checkin.missionTarget() != null) {
            MissionTarget.Builder mt = MissionTarget.newBuilder()
                    .setLat(checkin.missionTarget().lat())
                    .setLon(checkin.missionTarget().lon());
            if (checkin.missionTarget().altitudeM() != null)
                mt.setAltitudeM(checkin.missionTarget().altitudeM());
            builder.setMissionTarget(mt.build());
        }

        sendAndCheck(builder.build());
    }

    // ── SimulationEventPort — MOVE / FLOOD ────────────────────────────────────

    @Override
    public void publishMove(String entityId, MonitoredEntityType entityType,
                            Coordinate position, LvcOrigin lvc, Instant timestamp) {
        EventRequest request = EventRequest.newBuilder()
                .setAreaId("simulation")
                .setSource("virtual-ms-java-simulation")
                .setEventKind(EventKind.MOVE)
                .setEntityType(toProtoEntityType(entityType))
                .setLvc(lvc.toProto())
                .setTimestampMs(timestamp.toEpochMilli())
                .setEntityId(entityId)
                .mergeFrom(buildPayload(entityType, position))
                .build();

        sendAndCheck(request);
    }

    @Override
    public void publishFlood(String areaId, String severity, double waterLevelCm,
                             Coordinate centroid, LvcOrigin lvc) {
        Location loc = Location.newBuilder()
                .setLat(centroid.lat()).setLon(centroid.lon()).build();

        FloodAreaPayload flood = FloodAreaPayload.newBuilder()
                .setCentroid(loc)
                .setSeverity(severity)
                .setWaterLevelCm((float) waterLevelCm)
                .build();

        EventRequest request = EventRequest.newBuilder()
                .setAreaId(areaId)
                .setSource("virtual-ms-java-simulation")
                .setEventKind(EventKind.MOVE)
                .setEntityType(EntityType.FLOOD_AREA)
                .setLvc(lvc.toProto())
                .setTimestampMs(Instant.now().toEpochMilli())
                .setEntityId("flood-" + areaId)
                .setFloodArea(flood)
                .build();

        sendAndCheck(request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendAndCheck(EventRequest request) {
        EventAck ack = stub.sendEvent(request);
        if (!"accepted".equals(ack.getStatus())) {
            throw new InfrastructureException("Event rejected by event-hub: " + ack.getErrorMessage());
        }
    }

    private EventRequest buildPayload(MonitoredEntityType type, Coordinate pos) {
        Location location = Location.newBuilder()
                .setLat(pos.lat()).setLon(pos.lon()).build();

        EventRequest.Builder b = EventRequest.newBuilder();
        switch (type) {
            case DRONE -> b.setDrone(DronePayload.newBuilder().setLocation(location).build());
            case BUS -> b.setBus(BusPayload.newBuilder().setLocation(location).build());
            case VESSEL -> b.setVessel(VesselPayload.newBuilder().setLocation(location).build());
            default -> {
            } // FLOOD_AREA / RAIN_AREA handled separately
        }
        return b.build();
    }

    private EntityType toProtoEntityType(MonitoredEntityType type) {
        return switch (type) {
            case DRONE -> EntityType.DRONE;
            case BUS -> EntityType.BUS;
            case VESSEL -> EntityType.VESSEL;
            case FLOOD_AREA -> EntityType.FLOOD_AREA;
            case RAIN_AREA -> EntityType.RAIN_AREA;
        };
    }
}
