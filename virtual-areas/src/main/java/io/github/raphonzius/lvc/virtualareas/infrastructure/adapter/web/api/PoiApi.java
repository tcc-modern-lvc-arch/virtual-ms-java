package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.api;

import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreatePoiRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.PoiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Points of Interest", description = "Coordinate-based POI management")
@RequestMapping("/api/v1/pois")
public interface PoiApi {

    @Operation(summary = "List all POIs")
    @GetMapping
    ResponseEntity<List<PoiResponse>> getAll();

    @Operation(summary = "Get POI by ID")
    @GetMapping("/{id}")
    ResponseEntity<PoiResponse> getById(@PathVariable String id);

    @Operation(summary = "Create POI")
    @PostMapping
    ResponseEntity<PoiResponse> create(@RequestBody CreatePoiRequest request);

    @Operation(summary = "Update POI")
    @PutMapping("/{id}")
    ResponseEntity<PoiResponse> update(@PathVariable String id, @RequestBody CreatePoiRequest request);

    @Operation(summary = "Delete POI")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id);
}
