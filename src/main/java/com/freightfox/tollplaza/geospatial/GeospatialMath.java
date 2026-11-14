package com.freightfox.tollplaza.geospatial;

import java.util.List;

/**
 * Pure mathematical geospatial utility class.
 * Implements Haversine distance, local Cartesian projection,
 * point-to-segment perpendicular distance, and cumulative route distance calculation.
 */
public final class GeospatialMath {

    // Earth's mean radius in meters
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;
    public static final double EARTH_RADIUS_KM = 6_371.0;

    private GeospatialMath() {
        // Utility class
    }

    /**
     * Calculates the great-circle distance between two points on Earth using the Haversine formula.
     *
     * @return Distance in kilometers
     */
    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                   Math.cos(phi1) * Math.cos(phi2) *
                   Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates the great-circle distance between two coordinates in meters.
     */
    public static double haversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        return haversineDistance(lat1, lon1, lat2, lon2) * 1000.0;
    }

    /**
     * Represents the result of projecting a point onto a route polyline.
     *
     * @param minPerpendicularDistanceMeters Shortest distance from toll plaza to the route corridor in meters
     * @param distanceFromSourceKm Cumulative driving distance along the route from the starting point in km
     * @param isWithinCorridor Whether the toll lies within the configured corridor threshold
     */
    public record ProjectionResult(
            double minPerpendicularDistanceMeters,
            double distanceFromSourceKm,
            boolean isWithinCorridor
    ) {}

    /**
     * Evaluates whether a toll plaza is located along a polyline corridor
     * and calculates its precise cumulative travel distance from the origin.
     *
     * @param tollLat Toll plaza latitude
     * @param tollLon Toll plaza longitude
     * @param polyline Ordered list of route coordinates [P0, P1, ..., Pn]
     * @param corridorThresholdMeters Maximum allowed perpendicular distance to consider the toll on the route
     * @return ProjectionResult containing perpendicular distance and distance from source
     */
    public static ProjectionResult projectPointOntoPolyline(
            double tollLat,
            double tollLon,
            List<GeoCoordinate> polyline,
            double corridorThresholdMeters) {

        if (polyline == null || polyline.size() < 2) {
            return new ProjectionResult(Double.MAX_VALUE, 0.0, false);
        }

        double minDistanceMeters = Double.MAX_VALUE;
        double bestDistanceFromSourceKm = 0.0;

        // Cumulative distance of segments from origin
        double cumulativeKm = 0.0;

        for (int i = 0; i < polyline.size() - 1; i++) {
            GeoCoordinate pA = polyline.get(i);
            GeoCoordinate pB = polyline.get(i + 1);

            double segmentLengthKm = haversineDistance(
                    pA.latitude(), pA.longitude(),
                    pB.latitude(), pB.longitude());

            // Convert segment and point to local planar Cartesian coordinates centered at pA (in meters)
            double latRad = Math.toRadians(pA.latitude());
            double metersPerDegreeLat = 111_132.95;
            double metersPerDegreeLon = 111_412.84 * Math.cos(latRad);

            // Vector AB in meters
            double vX = (pB.longitude() - pA.longitude()) * metersPerDegreeLon;
            double vY = (pB.latitude() - pA.latitude()) * metersPerDegreeLat;
            double vLenSq = vX * vX + vY * vY;

            // Vector AT (from segment start to toll) in meters
            double wX = (tollLon - pA.longitude()) * metersPerDegreeLon;
            double wY = (tollLat - pA.latitude()) * metersPerDegreeLat;

            double t;
            if (vLenSq == 0.0) {
                t = 0.0;
            } else {
                // Projection factor t = (w . v) / |v|^2
                t = (wX * vX + wY * vY) / vLenSq;
                // Clamp t to segment bounds [0.0, 1.0]
                t = Math.max(0.0, Math.min(1.0, t));
            }

            // Projected point on segment (in meters from pA)
            double projX = t * vX;
            double projY = t * vY;

            // Perpendicular distance from toll to projection point
            double dX = wX - projX;
            double dY = wY - projY;
            double perpendicularDistanceMeters = Math.sqrt(dX * dX + dY * dY);

            if (perpendicularDistanceMeters < minDistanceMeters) {
                minDistanceMeters = perpendicularDistanceMeters;
                // Cumulative distance to segment start + distance along segment to projection
                double distanceAlongSegmentKm = t * segmentLengthKm;
                bestDistanceFromSourceKm = cumulativeKm + distanceAlongSegmentKm;
            }

            cumulativeKm += segmentLengthKm;
        }

        boolean withinCorridor = minDistanceMeters <= corridorThresholdMeters;
        double roundedDistanceKm = Math.round(bestDistanceFromSourceKm * 100.0) / 100.0;

        return new ProjectionResult(minDistanceMeters, roundedDistanceKm, withinCorridor);
    }
}

