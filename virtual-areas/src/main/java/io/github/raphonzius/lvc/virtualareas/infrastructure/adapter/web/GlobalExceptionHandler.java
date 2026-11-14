package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web;

import io.github.raphonzius.lvc.virtualareas.application.exception.ApplicationException;
import io.github.raphonzius.lvc.virtualareas.domain.exception.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        // DomainException / ApplicationException are sealed interfaces; the type patterns below
        // dispatch on the runtime class, the interface checks pick up every permitted subtype.
        return switch (ex) {
            case DomainException d -> error(d.statusCode(), d.message());
            case ApplicationException a -> error(a.statusCode(), a.message());
            default -> error(500, "Internal server error: " + ex.getMessage());
        };
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status,
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
