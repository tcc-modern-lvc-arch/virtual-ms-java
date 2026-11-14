package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.grpc;

import io.github.raphonzius.lvc.proto.virtualareas.AreaServiceGrpc;
import io.github.raphonzius.lvc.virtualareas.LogCapture;
import io.github.raphonzius.lvc.proto.virtualareas.CheckRequest;
import io.github.raphonzius.lvc.proto.virtualareas.CheckResponse;
import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.application.service.GeofenceMonitoringService;
import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.AreaAction;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AreaGrpcServiceTest {

    private final GeofenceMonitoringService monitoring = mock(GeofenceMonitoringService.class);
    private final AreaManagementService management = mock(AreaManagementService.class);
    private Server server;
    private ManagedChannel channel;
    private AreaServiceGrpc.AreaServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new AreaGrpcService(monitoring, management)).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = AreaServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
    }

    private static CheckRequest request(String entityType) {
        return CheckRequest.newBuilder().setEntityType(entityType).setLatitude(-23.5).setLongitude(-46.6).build();
    }

    @Test
    void matchedAreasAreReturned() {
        var area = new Area.CircleArea("a1", "Stop", "", new Coordinate(-23.5, -46.6), 50,
                List.of(MonitoredEntityType.BUS), AreaAction.CHECKIN_CHECKOUT, true, null, false);
        when(monitoring.checkPoint(any(), any())).thenReturn(List.of(area));

        CheckResponse response = stub.checkEntityInArea(request("bus"));

        assertThat(response.getMatchedAreasList()).singleElement()
                .satisfies(m -> assertThat(m.getAreaId()).isEqualTo("a1"));
    }

    @Test
    void unknownEntityTypeIsInvalidArgument() {
        assertThatThrownBy(() -> stub.checkEntityInArea(request("SPACESHIP")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(e.getStatus().getDescription()).contains("SPACESHIP");
                });
    }

    @Test
    void unexpectedFailureIsInternalWithGenericDescriptionAndLoggedOnServer() {
        when(monitoring.checkPoint(any(), any())).thenThrow(new IllegalStateException("db down at 10.0.0.5"));
        var logs = LogCapture.attach(AreaGrpcService.class);

        assertThatThrownBy(() -> stub.checkEntityInArea(request("DRONE")))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription()).doesNotContain("db down").doesNotContain("10.0.0.5");
                });
        assertThat(logs.errors()).singleElement().satisfies(ev ->
                assertThat(ev.getThrowableProxy().getMessage()).contains("db down at 10.0.0.5"));
        logs.detach();
    }

    @Test
    void getActiveAreasFailureDoesNotLeakTheMessageEither() {
        when(management.getActiveAreas()).thenThrow(new IllegalStateException("jdbc:mariadb://secret-host"));

        assertThatThrownBy(() -> stub.getActiveAreas(
                io.github.raphonzius.lvc.proto.virtualareas.GetActiveAreasRequest.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(e.getStatus().getDescription()).doesNotContain("secret-host");
                });
    }
}
