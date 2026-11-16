package io.github.raphonzius.lvc.virtualareas.domain.event;

import io.github.raphonzius.lvc.virtualareas.domain.geofence.Coordinate;

import java.util.Objects;

/**
 * Target POI of a MISSION_TRIGGER area — where constructive-airsim should dispatch the drone.
 */
public record MissionTarget(String poiId, String poiName, Coordinate coordinate) {
    public MissionTarget {
        Objects.requireNonNull(coordinate, "coordinate");
    }
}
