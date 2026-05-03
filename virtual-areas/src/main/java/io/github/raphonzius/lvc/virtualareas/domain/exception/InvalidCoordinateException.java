package io.github.raphonzius.lvc.virtualareas.domain.exception;

public final class InvalidCoordinateException extends RuntimeException implements DomainException {

    public InvalidCoordinateException(String reason) {
        super("Invalid coordinate: " + reason);
    }

    @Override
    public int statusCode() {
        return 400;
    }

    @Override
    public String message() {
        return getMessage();
    }
}
