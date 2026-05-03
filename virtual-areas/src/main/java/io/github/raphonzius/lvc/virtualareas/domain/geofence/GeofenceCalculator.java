package io.github.raphonzius.lvc.virtualareas.domain.geofence;

import io.github.raphonzius.lvc.virtualareas.domain.area.Area;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.List;

public final class GeofenceCalculator {

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final GeometryFactory JTS = new GeometryFactory();

    private GeofenceCalculator() {
    }

    public static boolean isInside(Coordinate point, Area area) {
        return switch (area) {
            case Area.PolygonArea p -> isInsidePolygon(point, p.vertices());
            case Area.CircleArea c -> isInsideCircle(point, c.center(), c.radiusMeters());
            case Area.CorridorArea co -> isInsideCorridor(point, co.centerline(), co.halfWidthMeters());
        };
    }

    private static boolean isInsidePolygon(Coordinate point, List<Coordinate> vertices) {
        org.locationtech.jts.geom.Coordinate[] coords = vertices.stream()
                .map(c -> new org.locationtech.jts.geom.Coordinate(c.lon(), c.lat()))
                .toArray(org.locationtech.jts.geom.Coordinate[]::new);
        Polygon polygon = JTS.createPolygon(coords);
        Point p = JTS.createPoint(new org.locationtech.jts.geom.Coordinate(point.lon(), point.lat()));
        return polygon.contains(p) || polygon.covers(p);
    }

    private static boolean isInsideCircle(Coordinate point, Coordinate center, double radiusMeters) {
        return haversineMeters(point, center) <= radiusMeters;
    }

    private static boolean isInsideCorridor(Coordinate point, List<Coordinate> centerline, double halfWidthMeters) {
        for (int i = 0; i < centerline.size() - 1; i++) {
            if (distanceToSegmentMeters(point, centerline.get(i), centerline.get(i + 1)) <= halfWidthMeters) {
                return true;
            }
        }
        return false;
    }

    public static double haversineMeters(Coordinate a, Coordinate b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    // Equirectangular projection — accurate enough for corridors < 1 km
    private static double distanceToSegmentMeters(Coordinate p, Coordinate a, Coordinate b) {
        double latScale = EARTH_RADIUS_M * Math.PI / 180.0;
        double lonScale = latScale * Math.cos(Math.toRadians(a.lat()));

        double px = (p.lon() - a.lon()) * lonScale;
        double py = (p.lat() - a.lat()) * latScale;
        double bx = (b.lon() - a.lon()) * lonScale;
        double by = (b.lat() - a.lat()) * latScale;

        double len2 = bx * bx + by * by;
        if (len2 == 0.0) return Math.sqrt(px * px + py * py);

        double t = Math.max(0.0, Math.min(1.0, (px * bx + py * by) / len2));
        double dx = px - t * bx;
        double dy = py - t * by;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
