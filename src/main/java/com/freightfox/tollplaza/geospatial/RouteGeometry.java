package com.freightfox.tollplaza.geospatial;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Encapsulates the driving route obtained from the routing provider,
 * including total highway distance and the ordered sequence of polyline coordinates.
 */
@Getter
@Builder
@ToString
public class RouteGeometry {

    private final double totalDistanceInKm;
    private final List<GeoCoordinate> coordinates;

    public boolean isEmpty() {
        return coordinates == null || coordinates.isEmpty();
    }

    public double getMinLatitude() {
        return coordinates.stream().mapToDouble(GeoCoordinate::latitude).min().orElse(0.0);
    }

    public double getMaxLatitude() {
        return coordinates.stream().mapToDouble(GeoCoordinate::latitude).max().orElse(0.0);
    }

    public double getMinLongitude() {
        return coordinates.stream().mapToDouble(GeoCoordinate::longitude).min().orElse(0.0);
    }

    public double getMaxLongitude() {
        return coordinates.stream().mapToDouble(GeoCoordinate::longitude).max().orElse(0.0);
    }
}

