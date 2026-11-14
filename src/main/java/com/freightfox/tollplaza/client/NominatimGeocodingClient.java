package com.freightfox.tollplaza.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geocoding client implementation using OpenStreetMap Nominatim with local fallback cache.
 */
@Component
@Slf4j
public class NominatimGeocodingClient implements GeocodingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String userAgent;

    // Cache of resolved pincodes
    private final Map<String, GeoCoordinate> pincodeCache = new ConcurrentHashMap<>();

    // Curated fallback coordinates for standard Indian pincodes to ensure 100% resilience against OSM rate limits
    private static final Map<String, GeoCoordinate> PRE_SEEDED_PINCODES = Map.of(
            "560064", new GeoCoordinate(13.1007, 77.5963), // Yelahanka, Bengaluru
            "411045", new GeoCoordinate(18.5158, 73.7806), // Bavdhan, Pune
            "110001", new GeoCoordinate(28.6315, 77.2167), // Connaught Place, New Delhi
            "400001", new GeoCoordinate(18.9322, 72.8354), // Fort, Mumbai
            "600001", new GeoCoordinate(13.0827, 80.2707), // Chennai Central
            "500001", new GeoCoordinate(17.3850, 78.4867), // Hyderabad
            "700001", new GeoCoordinate(22.5726, 88.3639), // BBD Bagh, Kolkata
            "380001", new GeoCoordinate(23.0225, 72.5714), // Ahmedabad
            "302001", new GeoCoordinate(26.9124, 75.7873), // Jaipur
            "226001", new GeoCoordinate(26.8467, 80.9462)  // Lucknow
    );

    public NominatimGeocodingClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.geocoding.nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${app.geocoding.nominatim.user-agent:FreightFox-TollPlazaService/1.0}") String userAgent) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
        this.pincodeCache.putAll(PRE_SEEDED_PINCODES);
    }

    @Override
    public Optional<GeoCoordinate> geocodePincode(String pincode) {
        if (pincode == null || pincode.isBlank()) {
            return Optional.empty();
        }

        // 1. Check local cache / pre-seeded coordinates
        if (pincodeCache.containsKey(pincode)) {
            log.debug("Pincode {} found in local geocoding cache", pincode);
            return Optional.of(pincodeCache.get(pincode));
        }

        // 2. Query Nominatim API
        try {
            log.info("Resolving pincode {} via Nominatim API...", pincode);
            String response = restClient.get()
                    .uri(baseUrl + "/search?postalcode={pincode}&country=India&format=json&limit=1", pincode)
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(String.class);

            if (response != null && !response.isBlank()) {
                JsonNode root = objectMapper.readTree(response);
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode first = root.get(0);
                    double lat = Double.parseDouble(first.get("lat").asText());
                    double lon = Double.parseDouble(first.get("lon").asText());
                    GeoCoordinate coordinate = new GeoCoordinate(lat, lon);
                    pincodeCache.put(pincode, coordinate);
                    log.info("Successfully geocoded pincode {} -> ({}, {})", pincode, lat, lon);
                    return Optional.of(coordinate);
                }
            }
        } catch (Exception e) {
            log.warn("Nominatim geocoding failed for pincode {}: {}", pincode, e.getMessage());
        }

        return Optional.empty();
    }
}

