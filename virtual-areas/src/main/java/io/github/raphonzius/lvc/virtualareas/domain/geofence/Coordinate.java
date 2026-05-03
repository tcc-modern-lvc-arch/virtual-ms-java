package io.github.raphonzius.lvc.virtualareas.domain.geofence;

public record Coordinate(double lat, double lon, Double altitudeM) {
    public Coordinate(double lat, double lon) {
        this(lat, lon, null);
    }
}
