package io.github.raphonzius.lvc.virtualareas.application.exception;

public sealed interface ApplicationException permits AreaApplicationException, PoiApplicationException, SimulationApplicationException {
    int statusCode();

    String message();
}
