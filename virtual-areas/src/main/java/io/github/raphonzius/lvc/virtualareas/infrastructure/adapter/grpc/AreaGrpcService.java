package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.grpc;

import io.github.raphonzius.lvc.proto.virtualareas.*;
import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.application.service.GeofenceMonitoringService;
import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import io.github.raphonzius.lvc.virtualareas.domain.area.MonitoredEntityType;
import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AreaGrpcService extends AreaServiceGrpc.AreaServiceImplBase {

    private final GeofenceMonitoringService monitoringService;
    private final AreaManagementService areaManagementService;

    private static String areaType(Area a) {
        return switch (a) {
            case Area.PolygonArea ignored -> "POLYGON";
            case Area.CircleArea ignored -> "CIRCLE";
            case Area.CorridorArea ignored -> "CORRIDOR";
        };
    }

    @Override
    public void checkEntityInArea(CheckRequest req, StreamObserver<CheckResponse> obs) {
        try {
            Coordinate position = new Coordinate(req.getLatitude(), req.getLongitude(),
                    req.hasAltitudeM() ? req.getAltitudeM() : null);
            MonitoredEntityType type = MonitoredEntityType.valueOf(req.getEntityType().toUpperCase());

            List<Area> matched = monitoringService.checkPoint(position, type);

            CheckResponse response = CheckResponse.newBuilder()
                    .addAllMatchedAreas(matched.stream()
                            .map(a -> AreaMatch.newBuilder()
                                    .setAreaId(a.id())
                                    .setAreaName(a.name())
                                    .setStatus("INSIDE")
                                    .build())
                            .toList())
                    .build();

            obs.onNext(response);
            obs.onCompleted();
        } catch (Exception e) {
            log.error("checkEntityInArea error", e);
            obs.onError(e);
        }
    }

    @Override
    public void getActiveAreas(GetActiveAreasRequest req, StreamObserver<GetActiveAreasResponse> obs) {
        try {
            List<Area> areas = areaManagementService.getActiveAreas().stream()
                    .filter(a -> req.getEntityTypeFilter().isBlank()
                            || a.monitoredEntityTypes().stream()
                            .anyMatch(t -> t.name().equalsIgnoreCase(req.getEntityTypeFilter())))
                    .toList();

            GetActiveAreasResponse response = GetActiveAreasResponse.newBuilder()
                    .addAllAreas(areas.stream()
                            .map(a -> AreaSummary.newBuilder()
                                    .setAreaId(a.id())
                                    .setName(a.name())
                                    .setAreaType(areaType(a))
                                    .addAllMonitoredEntityTypes(
                                            a.monitoredEntityTypes().stream().map(MonitoredEntityType::name).toList())
                                    .setAction(a.action().name())
                                    .setTargetPoiId(a.targetPoiId() != null ? a.targetPoiId() : "")
                                    .setPatrolZone(a.patrolZone())
                                    .build())
                            .toList())
                    .build();

            obs.onNext(response);
            obs.onCompleted();
        } catch (Exception e) {
            log.error("getActiveAreas error", e);
            obs.onError(e);
        }
    }
}
