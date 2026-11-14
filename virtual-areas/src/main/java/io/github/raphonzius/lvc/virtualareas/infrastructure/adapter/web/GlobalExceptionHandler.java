package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web;

import io.github.raphonzius.lvc.virtualareas.application.exception.ApplicationException;
import io.github.raphonzius.lvc.virtualareas.domain.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        // DomainException / ApplicationException are sealed interfaces; the type patterns below
        // dispatch on the runtime class, the interface checks pick up every permitted subtype.
        return switch (ex) {
            // Spring MVC's own exceptions (no handler/resource, bad method, bad request, ...) already
            // carry the right 4xx status: keep it instead of turning it into a 500.
            case ErrorResponse er -> {
                int status = er.getStatusCode().value();
                if (status >= 500) log.error("Request failed ({})", status, ex);
                else log.debug("Request rejected by Spring MVC ({}): {}", status, ex.getMessage());
                yield error(status, er.getBody().getDetail() != null ? er.getBody().getDetail() : ex.getMessage());
            }
            case DomainException d -> error(d.statusCode(), d.message());
            case ApplicationException a -> error(a.statusCode(), a.message());
            default -> {
                log.error("Unhandled exception", ex);
                yield error(500, "Internal server error");
            }
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
