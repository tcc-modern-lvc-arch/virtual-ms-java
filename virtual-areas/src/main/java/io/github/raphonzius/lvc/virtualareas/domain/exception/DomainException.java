package io.github.raphonzius.lvc.virtualareas.domain.exception;

public sealed interface DomainException permits AreaNotFoundException, InvalidCoordinateException {
    int statusCode();

    String message();
}
