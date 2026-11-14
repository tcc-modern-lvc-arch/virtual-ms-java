package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EventAck;
import io.github.raphonzius.lvc.proto.event.EventHubGrpc;
import io.github.raphonzius.lvc.proto.event.EventKind;
import io.github.raphonzius.lvc.proto.event.EventRequest;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;
import io.github.raphonzius.lvc.virtualareas.domain.event.LvcOrigin;
import io.github.raphonzius.lvc.virtualareas.domain.event.MissionTarget;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.github.raphonzius.lvc.virtualareas.infrastructure.config.properties.EventHubProperties;
import io.github.raphonzius.lvc.virtualareas.infrastructure.exception.InfrastructureException;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventHubGrpcAdapterTest {

    private Server server;
    private ManagedChannel channel;
    private final List<EventRequest> received = new CopyOnWriteArrayList<>();

    private EventHubGrpcAdapter adapterWith(Consumer<StreamObserver<EventAck>> responder, Duration deadline)
            throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new EventHubGrpc.EventHubImplBase() {
                    @Override
                    public void sendEvent(EventRequest request, StreamObserver<EventAck> obs) {
                        received.add(request);
                        responder.accept(obs);
                    }
                }).build().start();
        channel = InProcessChannelBuilder.forName(name).build();
        var props = new EventHubProperties(null, deadline, null, null, null);
        return new EventHubGrpcAdapter(EventHubGrpc.newBlockingStub(channel), props);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    private static AreaTransitionEvent.Checkin checkin() {
        return new AreaTransitionEvent.Checkin("area-1", "Area", "bus-1", MonitoredEntityType.BUS,
                new Coordinate(-23.5, -46.6), LvcOrigin.LIVE, Instant.now(),
                new MissionTarget("poi-1", "Stop", new Coordinate(-23.4, -46.5)));
    }

    @Test
    void dispatchSendsMappedCheckin() throws Exception {
        var adapter = adapterWith(obs -> {
            obs.onNext(EventAck.newBuilder().setStatus("accepted").build());
            obs.onCompleted();
        }, Duration.ofSeconds(2));

        adapter.dispatch(checkin());

        assertThat(received).singleElement().satisfies(r -> {
            assertThat(r.getEventKind()).isEqualTo(EventKind.CHECKIN);
            assertThat(r.getSource()).isEqualTo("virtual-ms-java");
            assertThat(r.getMissionTarget().getPoiId()).isEqualTo("poi-1");
        });
    }

    @Test
    void rejectedAckRaisesInfrastructureException() throws Exception {
        var adapter = adapterWith(obs -> {
            obs.onNext(EventAck.newBuilder().setStatus("rejected").setErrorMessage("nope").build());
            obs.onCompleted();
        }, Duration.ofSeconds(2));

        assertThatThrownBy(() -> adapter.dispatch(checkin()))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void hangingEventHubFailsFastWithDeadlineExceeded() throws Exception {
        var adapter = adapterWith(obs -> { /* never answers */ }, Duration.ofMillis(200));

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.dispatch(checkin()))
                .isInstanceOf(InfrastructureException.class)
                .cause()
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }
}
