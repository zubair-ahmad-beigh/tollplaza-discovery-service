package com.freightfox.tollplaza.geospatial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class GeospatialMathTest {

    @Test
    @DisplayName("Haversine distance should compute accurate great-circle distance between Bengaluru and Pune")
    void testHaversineDistanceBetweenBengaluruAndPune() {
        // Bengaluru (Yelahanka): 13.1007 N, 77.5963 E
        // Pune (Bavdhan): 18.5158 N, 73.7806 E
        double distanceKm = GeospatialMath.haversineDistance(13.1007, 77.5963, 18.5158, 73.7806);

        // Expected straight line distance is ~734 km
        assertThat(distanceKm).isCloseTo(734.0, offset(15.0));
    }

    @Test
    @DisplayName("Should detect toll plaza directly on route corridor")
    void testTollPlazaDirectlyOnRoute() {
        // Route: P0 (13.0, 77.0) -> P1 (14.0, 77.0) -> P2 (15.0, 77.0) [straight north along 77° E]
        List<GeoCoordinate> polyline = List.of(
                new GeoCoordinate(13.0, 77.0),
                new GeoCoordinate(14.0, 77.0),
                new GeoCoordinate(15.0, 77.0)
        );

        // Toll located at (13.5, 77.0) - exactly on segment P0-P1
        GeospatialMath.ProjectionResult result = GeospatialMath.projectPointOntoPolyline(
                13.5, 77.0, polyline, 500.0);

        assertThat(result.isWithinCorridor()).isTrue();
        assertThat(result.minPerpendicularDistanceMeters()).isLessThan(5.0); // essentially 0m
        // 0.5 degrees lat ≈ 55.5 km from source
        assertThat(result.distanceFromSourceKm()).isCloseTo(55.6, offset(1.0));
    }

    @Test
    @DisplayName("Should detect toll plaza within 200m corridor threshold")
    void testTollPlazaNearRouteWithinCorridor() {
        // Route segment from (18.0, 73.0) to (18.0, 74.0) [due east]
        List<GeoCoordinate> polyline = List.of(
                new GeoCoordinate(18.0, 73.0),
                new GeoCoordinate(18.0, 74.0)
        );

        // Toll offset slightly north by 0.001 degrees lat (~111 meters)
        GeospatialMath.ProjectionResult result = GeospatialMath.projectPointOntoPolyline(
                18.001, 73.5, polyline, 200.0);

        assertThat(result.isWithinCorridor()).isTrue();
        assertThat(result.minPerpendicularDistanceMeters()).isCloseTo(111.0, offset(5.0));
    }

    @Test
    @DisplayName("Should reject toll plaza far away from route")
    void testTollPlazaFarFromRoute() {
        List<GeoCoordinate> polyline = List.of(
                new GeoCoordinate(13.0, 77.0),
                new GeoCoordinate(14.0, 77.0)
        );

        // Toll located 10 km to the east (77.10)
        GeospatialMath.ProjectionResult result = GeospatialMath.projectPointOntoPolyline(
                13.5, 77.10, polyline, 500.0);

        assertThat(result.isWithinCorridor()).isFalse();
        assertThat(result.minPerpendicularDistanceMeters()).isGreaterThan(5000.0);
    }
}

