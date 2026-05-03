package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web;

import io.github.raphonzius.lvc.virtualareas.application.service.EventSimulationService;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.api.SimulationApi;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.SimulateFloodRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.SimulateMoveRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.TransitionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SimulationController implements SimulationApi {

    private final EventSimulationService service;

    @Override
    public ResponseEntity<List<TransitionResult>> pingMove(SimulateMoveRequest req) {
        List<TransitionResult> results = service
                .pingMoveEvent(req.entityId(), req.entityType(), req.lat(), req.lon(),
                        req.altitudeM(), req.lvc())
                .stream()
                .map(TransitionResult::from)
                .toList();
        return ResponseEntity.ok(results);
    }

    @Override
    public ResponseEntity<Void> simulateFlood(SimulateFloodRequest req) {
        service.simulateFloodEvent(req.areaId(), req.severity(), req.waterLevelCm(), req.lvc());
        return ResponseEntity.accepted().build();
    }
}
