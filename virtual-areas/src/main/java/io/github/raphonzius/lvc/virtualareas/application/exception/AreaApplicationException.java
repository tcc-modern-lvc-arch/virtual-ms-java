package io.github.raphonzius.lvc.virtualareas.application.exception;

public final class AreaApplicationException extends RuntimeException implements ApplicationException {

    private final int statusCode;

    public AreaApplicationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public String message() {
        return getMessage();
    }
}
