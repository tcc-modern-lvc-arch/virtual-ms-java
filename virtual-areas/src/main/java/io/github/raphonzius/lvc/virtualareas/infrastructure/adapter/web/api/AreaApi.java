package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.api;

import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.AreaResponse;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreateAreaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Areas", description = "Virtual geofencing area management")
@RequestMapping("/api/v1/areas")
public interface AreaApi {

    @Operation(summary = "List all areas")
    @GetMapping
    ResponseEntity<List<AreaResponse>> getAll();

    @Operation(summary = "Get area by ID")
    @GetMapping("/{id}")
    ResponseEntity<AreaResponse> getById(@PathVariable String id);

    @Operation(summary = "List active areas")
    @GetMapping("/active")
    ResponseEntity<List<AreaResponse>> getActive();

    @Operation(summary = "Create area")
    @PostMapping
    ResponseEntity<AreaResponse> create(@RequestBody CreateAreaRequest request);

    @Operation(summary = "Update area")
    @PutMapping("/{id}")
    ResponseEntity<AreaResponse> update(@PathVariable String id, @RequestBody CreateAreaRequest request);

    @Operation(summary = "Toggle area active state")
    @PatchMapping("/{id}/toggle")
    ResponseEntity<AreaResponse> toggleActive(@PathVariable String id);

    @Operation(summary = "Delete area")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id);
}
