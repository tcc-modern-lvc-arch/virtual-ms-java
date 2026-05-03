package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.*;
import io.github.raphonzius.lvc.virtualareas.application.service.GeofenceMonitoringService;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventHubSubscriberAdapter implements InitializingBean, DisposableBean {

    private static final List<MonitoredEntityType> TRACKED = List.of(
            MonitoredEntityType.DRONE, MonitoredEntityType.BUS, MonitoredEntityType.VESSEL);

    private final EventHubGrpc.EventHubStub asyncStub;
    private final GeofenceMonitoringService monitoringService;

    private volatile boolean running = true;

    private static EntityType toProto(MonitoredEntityType t) {
        return switch (t) {
            case DRONE -> EntityType.DRONE;
            case BUS -> EntityType.BUS;
            case VESSEL -> EntityType.VESSEL;
            case FLOOD_AREA -> EntityType.FLOOD_AREA;
            case RAIN_AREA -> EntityType.RAIN_AREA;
        };
    }

    private static MonitoredEntityType fromProto(EntityType t) {
        return switch (t) {
            case DRONE -> MonitoredEntityType.DRONE;
            case BUS -> MonitoredEntityType.BUS;
            case VESSEL -> MonitoredEntityType.VESSEL;
            case FLOOD_AREA -> MonitoredEntityType.FLOOD_AREA;
            case RAIN_AREA -> MonitoredEntityType.RAIN_AREA;
            default -> MonitoredEntityType.DRONE;
        };
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void afterPropertiesSet() {
        TRACKED.forEach(type ->
                Thread.ofVirtual()
                        .name("grpc-sub-" + type.name().toLowerCase())
                        .start(() -> subscribeWithRetry(type))
        );
        log.info("Started {} gRPC subscription threads for event-hub", TRACKED.size());
    }

    @Override
    public void destroy() {
        running = false;
    }

    private void subscribeWithRetry(MonitoredEntityType type) {
        while (running) {
            try {
                CountDownLatch latch = new CountDownLatch(1);

                asyncStub.subscribe(
                        SubscribeRequest.newBuilder()
                                .setEntityType(toProto(type))
                                .build(),
                        new StreamObserver<>() {
                            @Override
                            public void onNext(EventRequest event) {
                                if (event.getEventKind() == EventKind.MOVE) {
                                    handleMove(event);
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                log.warn("event-hub subscription error [{}]: {}", type, t.getMessage());
                                latch.countDown();
                            }

                            @Override
                            public void onCompleted() {
                                log.info("event-hub subscription completed [{}]", type);
                                latch.countDown();
                            }
                        });

                latch.await();
                if (running) sleep(Duration.ofSeconds(5));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in [{}] subscriber, retrying in 5s", type, e);
                sleep(Duration.ofSeconds(5));
            }
        }
    }

    private void handleMove(EventRequest event) {
        try {
            Location loc = switch (event.getPayloadCase()) {
                case DRONE -> event.getDrone().getLocation();
                case BUS -> event.getBus().getLocation();
                case VESSEL -> event.getVessel().getLocation();
                default -> null;
            };
            if (loc == null) return;

            Coordinate position = new Coordinate(loc.getLat(), loc.getLon(),
                    loc.hasAltitudeM() ? loc.getAltitudeM() : null);

            monitoringService.processMove(
                    event.getEntityId(),
                    fromProto(event.getEntityType()),
                    position,
                    LvcOrigin.fromProto(event.getLvc()),
                    Instant.ofEpochMilli(event.getTimestampMs())
            );
        } catch (Exception e) {
            log.error("Error processing MOVE event entity={}", event.getEntityId(), e);
        }
    }
}
