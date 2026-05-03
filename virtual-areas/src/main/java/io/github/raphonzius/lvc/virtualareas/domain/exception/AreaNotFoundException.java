package io.github.raphonzius.lvc.virtualareas.domain.exception;

public final class AreaNotFoundException extends RuntimeException implements DomainException {

    private final String areaId;

    public AreaNotFoundException(String areaId) {
        super("Area not found: " + areaId);
        this.areaId = areaId;
    }

    @Override
    public int statusCode() {
        return 404;
    }

    @Override
    public String message() {
        return "Area not found: " + areaId;
    }
}
