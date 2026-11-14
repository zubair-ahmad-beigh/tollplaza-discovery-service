package com.freightfox.tollplaza.client;

import com.freightfox.tollplaza.geospatial.GeoCoordinate;

import java.util.Optional;

/**
 * Contract for converting an Indian postal code (pincode) into geographical coordinates.
 */
public interface GeocodingClient {

    /**
     * Resolves a 6-digit Indian pincode into a latitude/longitude coordinate centroid.
     *
     * @param pincode 6-digit Indian pincode
     * @return Optional containing GeoCoordinate if resolved, empty otherwise
     */
    Optional<GeoCoordinate> geocodePincode(String pincode);
}

