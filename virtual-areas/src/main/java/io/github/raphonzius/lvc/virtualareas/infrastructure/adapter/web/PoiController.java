package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web;

import io.github.raphonzius.lvc.virtualareas.application.service.AreaManagementService;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.api.PoiApi;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.CreatePoiRequest;
import io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto.PoiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PoiController implements PoiApi {

    private final AreaManagementService service;

    @Override
    public ResponseEntity<List<PoiResponse>> getAll() {
        return ResponseEntity.ok(service.getAllPois().stream().map(PoiResponse::from).toList());
    }

    @Override
    public ResponseEntity<PoiResponse> getById(String id) {
        return ResponseEntity.ok(PoiResponse.from(service.getPoi(id)));
    }

    @Override
    public ResponseEntity<PoiResponse> create(CreatePoiRequest request) {
        return ResponseEntity.status(201).body(PoiResponse.from(service.createPoi(request)));
    }

    @Override
    public ResponseEntity<PoiResponse> update(String id, CreatePoiRequest request) {
        return ResponseEntity.ok(PoiResponse.from(service.updatePoi(id, request)));
    }

    @Override
    public ResponseEntity<Void> delete(String id) {
        service.deletePoi(id);
        return ResponseEntity.noContent().build();
    }
}
