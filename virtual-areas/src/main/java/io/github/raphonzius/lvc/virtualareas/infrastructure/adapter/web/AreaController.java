package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web;

import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.api.AreaApi;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.AreaResponse;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreateAreaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AreaController implements AreaApi {

    private final AreaManagementService service;

    @Override
    public ResponseEntity<List<AreaResponse>> getAll() {
        return ResponseEntity.ok(service.getAllAreas().stream().map(AreaResponse::from).toList());
    }

    @Override
    public ResponseEntity<AreaResponse> getById(String id) {
        return ResponseEntity.ok(AreaResponse.from(service.getArea(id)));
    }

    @Override
    public ResponseEntity<List<AreaResponse>> getActive() {
        return ResponseEntity.ok(service.getActiveAreas().stream().map(AreaResponse::from).toList());
    }

    @Override
    public ResponseEntity<AreaResponse> create(CreateAreaRequest request) {
        return ResponseEntity.status(201).body(AreaResponse.from(service.createArea(request)));
    }

    @Override
    public ResponseEntity<AreaResponse> update(String id, CreateAreaRequest request) {
        return ResponseEntity.ok(AreaResponse.from(service.updateArea(id, request)));
    }

    @Override
    public ResponseEntity<AreaResponse> toggleActive(String id) {
        return ResponseEntity.ok(AreaResponse.from(service.toggleActive(id)));
    }

    @Override
    public ResponseEntity<Void> delete(String id) {
        service.deleteArea(id);
        return ResponseEntity.noContent().build();
    }
}
