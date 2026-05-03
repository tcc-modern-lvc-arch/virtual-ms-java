package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.api;

import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.SimulateFloodRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.SimulateMoveRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.TransitionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Tag(name = "Simulation", description = "Simulate virtual LVC events")
@RequestMapping("/api/v1/simulate")
public interface SimulationApi {

    @Operation(summary = "Ping a virtual MOVE event and return detected transitions")
    @PostMapping("/move")
    ResponseEntity<List<TransitionResult>> pingMove(@RequestBody SimulateMoveRequest request);

    @Operation(summary = "Simulate a virtual flood event")
    @PostMapping("/flood")
    ResponseEntity<Void> simulateFlood(@RequestBody SimulateFloodRequest request);
}
