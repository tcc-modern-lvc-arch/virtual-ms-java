package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web;

import io.github.raphonzius.lvc.virtualareas.LogCapture;
import io.github.raphonzius.lvc.virtualareas.domain.exception.AreaNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private LogCapture logs;

    @BeforeEach
    void attach() {
        logs = LogCapture.attach(GlobalExceptionHandler.class);
    }

    @AfterEach
    void detach() {
        logs.detach();
    }

    @Test
    void unexpectedExceptionIsLoggedWithStackTraceAndAnsweredWithGenericMessage() {
        var response = handler.handle(new IllegalStateException("jdbc:mariadb://secret-host/virtual_areas"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("status", 500);
        assertThat(response.getBody().get("message").toString()).doesNotContain("secret-host");
        assertThat(logs.errors()).singleElement().satisfies(e -> {
            assertThat(e.getFormattedMessage()).isEqualTo("Unhandled exception");
            assertThat(e.getThrowableProxy().getMessage()).contains("secret-host");
        });
    }

    @Test
    void springMvcNotFoundKeepsItsStatus() {
        var response = handler.handle(new NoResourceFoundException(HttpMethod.GET, "/api/nope", "api/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(logs.errors()).isEmpty();
    }

    @Test
    void otherSpringErrorResponsesKeepTheirStatus() {
        assertThat(handler.handle(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad input"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handle(new HttpRequestMethodNotSupportedException("PATCH"))
                .getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void domainExceptionsStillMapToTheirOwnStatus() {
        var response = handler.handle(new AreaNotFoundException("area-9"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(logs.errors()).isEmpty();
    }
}
