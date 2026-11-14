package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.Location;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.MissionTarget;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProtoMapperTest {

    private static final Instant TS = Instant.ofEpochMilli(1_700_000_000_000L);

    @ParameterizedTest
    @EnumSource(MonitoredEntityType.class)
    void entityTypeRoundTrips(MonitoredEntityType type) {
        assertThat(ProtoMapper.fromProto(ProtoMapper.toProto(type))).contains(type);
    }

    @Test
    void unrecognizedEntityTypeIsEmptyInsteadOfDefaultingToDrone() {
        assertThat(ProtoMapper.fromProto(EntityType.UNRECOGNIZED)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(LvcOrigin.class)
    void lvcRoundTrips(LvcOrigin lvc) {
        assertThat(ProtoMapper.fromProto(ProtoMapper.toProto(lvc))).contains(lvc);
    }

    @Test
    void unrecognizedLvcIsEmpty() {
        assertThat(ProtoMapper.fromProto(io.github.raphonzius.lvc.proto.event.LvcOrigin.UNRECOGNIZED)).isEmpty();
    }

    @Test
    void coordinateLocationRoundTripKeepsOptionalAltitude() {
        var withAlt = new Coordinate(-23.5, -46.6, 120.0);
        var noAlt = new Coordinate(-23.5, -46.6);

        assertThat(ProtoMapper.toCoordinate(ProtoMapper.toLocation(withAlt))).isEqualTo(withAlt);
        assertThat(ProtoMapper.toLocation(noAlt).hasAltitudeM()).isFalse();
        assertThat(ProtoMapper.toCoordinate(ProtoMapper.toLocation(noAlt))).isEqualTo(noAlt);
    }

    @Test
    void checkinBecomesEventRequestWithPayloadAndMissionTarget() {
        var target = new MissionTarget("poi-1", "Caio Prado", new Coordinate(-23.55, -46.65, 30.0));
        var checkin = new AreaTransitionEvent.Checkin("area-1", "Area 1", "bus-42",
                MonitoredEntityType.BUS, new Coordinate(-23.5, -46.6), LvcOrigin.LIVE, TS, target);

        EventRequest req = ProtoMapper.toEventRequest(checkin, "virtual-ms-java");

        assertThat(req.getEventKind()).isEqualTo(EventKind.CHECKIN);
        assertThat(req.getAreaId()).isEqualTo("area-1");
        assertThat(req.getSource()).isEqualTo("virtual-ms-java");
        assertThat(req.getEntityType()).isEqualTo(EntityType.BUS);
        assertThat(req.getEntityId()).isEqualTo("bus-42");
        assertThat(req.getLvc()).isEqualTo(io.github.raphonzius.lvc.proto.event.LvcOrigin.LIVE);
        assertThat(req.getTimestampMs()).isEqualTo(TS.toEpochMilli());
        assertThat(req.getPayloadCase()).isEqualTo(EventRequest.PayloadCase.BUS);
        assertThat(req.getBus().getLocation().getLat()).isEqualTo(-23.5);
        assertThat(req.hasMissionTarget()).isTrue();
        assertThat(req.getMissionTarget().getPoiId()).isEqualTo("poi-1");
        assertThat(req.getMissionTarget().getPoiName()).isEqualTo("Caio Prado");
        assertThat(req.getMissionTarget().getLat()).isEqualTo(-23.55);
        assertThat(req.getMissionTarget().getAltitudeM()).isEqualTo(30.0);
    }

    @Test
    void checkoutHasNoMissionTarget() {
        var checkout = new AreaTransitionEvent.Checkout("area-1", "Area 1", "drone-1",
                MonitoredEntityType.DRONE, new Coordinate(1, 2), LvcOrigin.CONSTRUCTIVE, TS);

        EventRequest req = ProtoMapper.toEventRequest(checkout, "virtual-ms-java");

        assertThat(req.getEventKind()).isEqualTo(EventKind.CHECKOUT);
        assertThat(req.getPayloadCase()).isEqualTo(EventRequest.PayloadCase.DRONE);
        assertThat(req.hasMissionTarget()).isFalse();
    }

    @Test
    void locationOfReadsMovingEntityPayloads() {
        Location loc = Location.newBuilder().setLat(1).setLon(2).build();
        var vessel = EventRequest.newBuilder()
                .setVessel(io.github.raphonzius.lvc.proto.event.VesselPayload.newBuilder().setLocation(loc)).build();

        assertThat(ProtoMapper.locationOf(vessel)).contains(new Coordinate(1, 2));
        assertThat(ProtoMapper.locationOf(EventRequest.getDefaultInstance())).isEmpty();
    }
}
