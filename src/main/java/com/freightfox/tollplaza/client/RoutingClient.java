package com.freightfox.tollplaza.client;

import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.RouteGeometry;

/**
 * Contract for fetching driving routes, road geometries, and distances between coordinates.
 */
public interface RoutingClient {

    /**
     * Calculates the driving route between source and destination coordinates.
     *
     * @param source Source coordinate
     * @param destination Destination coordinate
     * @return RouteGeometry containing distance in km and ordered coordinate waypoints
     */
    RouteGeometry calculateRoute(GeoCoordinate source, GeoCoordinate destination);
}

