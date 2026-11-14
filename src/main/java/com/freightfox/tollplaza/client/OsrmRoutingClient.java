package com.freightfox.tollplaza.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.tollplaza.exception.RoutingException;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Real routing client implementation using OSRM (Open Source Routing Machine).
 * Fetches actual driving route geometries and distances over OSM road networks.
 *
 * Strict Error Policy:
 * In accordance with assignment requirements, failures or timeouts immediately throw
 * RoutingException, resulting in a clean HTTP 502 Bad Gateway response rather than
 * degraded synthetic fallbacks.
 */
@Component("osrmRoutingClient")
@Slf4j
public class OsrmRoutingClient implements RoutingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OsrmRoutingClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.routing.osrm.base-url:https://router.project-osrm.org}") String baseUrl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public RouteGeometry calculateRoute(GeoCoordinate source, GeoCoordinate destination) {
        log.info("Requesting driving route from OSRM: ({}, {}) -> ({}, {})...",
                source.latitude(), source.longitude(), destination.latitude(), destination.longitude());

        try {
            // OSRM format: /route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson
            String uri = String.format("%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                    baseUrl,
                    source.longitude(), source.latitude(),
                    destination.longitude(), destination.latitude());

            String response = restClient.get()
                    .uri(uri)
                    .header("User-Agent", "FreightFox-TollPlazaService/1.0 (Java 21)")
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new RoutingException("Empty response received from OSRM routing provider");
            }

            JsonNode root = objectMapper.readTree(response);
            String code = root.path("code").asText();

            if (!"Ok".equalsIgnoreCase(code)) {
                log.error("OSRM returned non-OK response code: {}", code);
                throw new RoutingException("OSRM routing failed with status code: " + code);
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                throw new RoutingException("No drivable route found between source and destination");
            }

            JsonNode primaryRoute = routes.get(0);
            double distanceMeters = primaryRoute.path("distance").asDouble();
            double distanceKm = Math.round((distanceMeters / 1000.0) * 100.0) / 100.0;

            JsonNode coordsArray = primaryRoute.path("geometry").path("coordinates");
            List<GeoCoordinate> coordinates = new ArrayList<>();

            if (coordsArray.isArray()) {
                for (JsonNode coord : coordsArray) {
                    // GeoJSON format is [longitude, latitude]
                    double lon = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    coordinates.add(new GeoCoordinate(lat, lon));
                }
            }

            if (coordinates.size() < 2) {
                throw new RoutingException("OSRM returned insufficient polyline waypoints for route geometry");
            }

            log.info("OSRM driving route successfully resolved: {} km with {} coordinate points",
                    distanceKm, coordinates.size());

            return RouteGeometry.builder()
                    .totalDistanceInKm(distanceKm)
                    .coordinates(coordinates)
                    .build();

        } catch (RoutingException re) {
            throw re;
        } catch (Exception e) {
            log.error("OSRM routing service failed: {}", e.getMessage(), e);
            throw new RoutingException("Routing provider unavailable: " + e.getMessage(), e);
        }
    }
}
