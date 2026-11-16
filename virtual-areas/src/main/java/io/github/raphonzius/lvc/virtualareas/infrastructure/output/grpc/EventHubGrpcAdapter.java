package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EntityType;
import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.proto.event.FloodAreaPayload;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.EventDispatchPort;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.SimulationEventPort;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.EventHubProperties;
import io.github.raphonzius.lvc.virtualareas.infrastructure.exception.InfrastructureException;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class EventHubGrpcAdapter implements EventDispatchPort, SimulationEventPort, EventRequestSender {

    public static final String SOURCE = "virtual-ms-java";
    static final String SIMULATION_SOURCE = "virtual-ms-java-simulation";

    private final EventHubGrpc.EventHubBlockingStub stub;
    private final Duration deadline;

    public EventHubGrpcAdapter(EventHubGrpc.EventHubBlockingStub stub, EventHubProperties props) {
        this.stub = stub;
        this.deadline = props.deadline();
    }

    // ── EventDispatchPort — CHECKIN / CHECKOUT ─────────────────────────────────

    @Override
    public void dispatch(AreaTransitionEvent event) {
        sendAndCheck(ProtoMapper.toEventRequest(event, SOURCE));
    }

    // ── SimulationEventPort — MOVE / FLOOD ────────────────────────────────────

    @Override
    public void publishMove(String entityId, MonitoredEntityType entityType,
                            Coordinate position, LvcOrigin lvc, Instant timestamp) {
        sendAndCheck(ProtoMapper.moveRequest("simulation", SIMULATION_SOURCE,
                entityId, entityType, position, lvc, timestamp));
    }

    @Override
    public void publishFlood(String areaId, String severity, double waterLevelCm,
                             Coordinate centroid, LvcOrigin lvc) {
        FloodAreaPayload flood = FloodAreaPayload.newBuilder()
                .setCentroid(ProtoMapper.toLocation(centroid))
                .setSeverity(severity)
                .setWaterLevelCm((float) waterLevelCm)
                .build();

        EventRequest request = EventRequest.newBuilder()
                .setAreaId(areaId)
                .setSource(SIMULATION_SOURCE)
                .setEventKind(EventKind.MOVE)
                .setEntityType(EntityType.FLOOD_AREA)
                .setLvc(ProtoMapper.toProto(lvc))
                .setTimestampMs(Instant.now().toEpochMilli())
                .setEntityId("flood-" + areaId)
                .setFloodArea(flood)
                .build();

        sendAndCheck(request);
    }

    /** Used by the outbox relay: payloads are stored already serialized. */
    @Override
    public void send(EventRequest request) {
        sendAndCheck(request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendAndCheck(EventRequest request) {
        EventAck ack;
        try {
            // Deadline is applied per call: a stub-level deadline would be an absolute instant
            // shared by every later call made with the same stub.
            ack = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS).sendEvent(request);
        } catch (StatusRuntimeException e) {
            throw new InfrastructureException("event-hub SendEvent failed (%s): %s"
                    .formatted(e.getStatus().getCode(), e.getStatus().getDescription()), e);
        }
        if (!"accepted".equals(ack.getStatus())) {
            throw new InfrastructureException("Event rejected by event-hub: " + ack.getErrorMessage());
        }
    }
}
