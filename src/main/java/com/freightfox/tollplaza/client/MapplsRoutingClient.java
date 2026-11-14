package com.freightfox.tollplaza.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.tollplaza.exception.RoutingException;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Official Mappls (MapmyIndia) Routing Client using Static API Key authentication.
 * Queries the official Mappls Advanced Driving Route API (Route ADV) for highway geometries.
 *
 * Credentials required via environment variable:
 * - MAPPLS_API_KEY
 */
@Component("mapplsRoutingClient")
@Slf4j
public class MapplsRoutingClient implements RoutingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String whitelistedIp;

    @Autowired
    public MapplsRoutingClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            @Value("${app.routing.mappls.api-key:}") String apiKey,
            @Value("${app.routing.mappls.base-url:https://route.mappls.com/route/direction}") String baseUrl,
            @Value("${app.routing.mappls.whitelisted-ip:}") String whitelistedIp) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://route.mappls.com/route/direction";
        this.whitelistedIp = whitelistedIp != null ? whitelistedIp.trim() : "";
    }

    public MapplsRoutingClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            String apiKey,
            String baseUrl) {
        this(restClient, objectMapper, apiKey, baseUrl, "");
    }

    /**
     * Checks whether a valid Mappls Static API Key has been configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public RouteGeometry calculateRoute(GeoCoordinate source, GeoCoordinate destination) {
        if (!isConfigured()) {
            throw new RoutingException("Mappls Static API Key (MAPPLS_API_KEY) is not configured. " +
                    "Please set it as an environment variable or switch the provider to 'osrm'.");
        }

        log.info("Requesting driving route from Mappls: ({}, {}) -> ({}, {})...",
                source.latitude(), source.longitude(), destination.latitude(), destination.longitude());

        try {
            String cleanBaseUrl = baseUrl.replaceAll("/+$", "");
            String uri;
            if (cleanBaseUrl.contains("advancedmaps")) {
                // Legacy MapmyIndia Advanced Maps format
                uri = String.format(java.util.Locale.ROOT,
                        "%s/%s/route_adv/driving/%.6f,%.6f;%.6f,%.6f?geometries=geojson&overview=full",
                        cleanBaseUrl, apiKey,
                        source.longitude(), source.latitude(),
                        destination.longitude(), destination.latitude());
            } else {
                // Official Mappls Route ADV format: {baseUrl}/route_adv/driving/{coords}?overview=full&geometries=geojson&access_token={key}
                uri = String.format(java.util.Locale.ROOT,
                        "%s/route_adv/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&access_token=%s",
                        cleanBaseUrl,
                        source.longitude(), source.latitude(),
                        destination.longitude(), destination.latitude(),
                        apiKey);
            }

            var requestSpec = restClient.get()
                    .uri(uri)
                    .header("User-Agent", "FreightFox-TollPlazaService/1.0 (Java 21)");

            if (whitelistedIp != null && !whitelistedIp.isBlank()) {
                requestSpec.header("X-Forwarded-For", whitelistedIp);
            }

            String response = requestSpec.retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                throw new RoutingException("Empty response received from Mappls routing service");
            }

            JsonNode root = objectMapper.readTree(response);

            // Mappls returns response code either as string ("ok", "NoRoute") or integer (200, 0)
            JsonNode codeNode = root.path("code");
            String codeStr = codeNode.asText();
            int responseCode = codeNode.isNumber() ? codeNode.asInt() : (codeStr.equalsIgnoreCase("ok") ? 200 : -1);

            if (responseCode != 200 && responseCode != 0) {
                String errorMsg = root.path("error_description").asText(root.path("message").asText(codeStr));
                throw new RoutingException("Mappls route calculation error: " + errorMsg + " (code: " + codeStr + ")");
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                throw new RoutingException("No routes found between source and destination in Mappls");
            }

            JsonNode primaryRoute = routes.get(0);
            double distanceMeters = primaryRoute.path("distance").asDouble();
            double distanceKm = Math.round((distanceMeters / 1000.0) * 100.0) / 100.0;

            // Geometry can be a GeoJSON object or an encoded polyline string
            List<GeoCoordinate> coordinates = parseGeometry(primaryRoute.path("geometry"));

            if (coordinates.size() < 2) {
                throw new RoutingException("Mappls returned insufficient polyline points for route geometry");
            }

            log.info("Mappls driving route successfully resolved: {} km with {} coordinate points",
                    distanceKm, coordinates.size());

            return RouteGeometry.builder()
                    .totalDistanceInKm(distanceKm)
                    .coordinates(coordinates)
                    .build();

        } catch (HttpClientErrorException.Unauthorized e) {
            String errorBody = "";
            try {
                errorBody = e.getResponseBodyAsString();
            } catch (Exception ignored) {
            }
            if (errorBody != null && errorBody.contains("IP/Domain validation failed")) {
                log.error("Mappls IP/Domain validation failed: HTTP 401 Unauthorized (Whitelisted IP does not match request origin)");
                throw new RoutingException("Mappls API key error: IP/Domain validation failed. Please check IP whitelisting in Mappls console (HTTP 401)");
            } else {
                log.error("Mappls API key authentication failed: HTTP 401 Unauthorized");
                throw new RoutingException("Mappls API key is unauthorized or invalid (HTTP 401)");
            }
        } catch (HttpClientErrorException.Forbidden e) {
            log.error("Mappls API access forbidden: HTTP 403 Forbidden (quota limit or unwhitelisted IP/domain)");
            throw new RoutingException("Mappls API access forbidden or quota exceeded (HTTP 403)");
        } catch (RestClientResponseException e) {
            String safeMsg = sanitize(e.getMessage());
            log.error("Mappls HTTP error {}: {}", e.getStatusCode(), safeMsg);
            throw new RoutingException("Mappls routing API HTTP " + e.getStatusCode().value() + " error: " + safeMsg);
        } catch (RoutingException re) {
            throw re;
        } catch (Exception e) {
            String safeMsg = sanitize(e.getMessage());
            log.error("Mappls route request failed: {}", safeMsg);
            throw new RoutingException("Failed to calculate driving route from Mappls: " + safeMsg);
        }
    }

    /**
     * Parses route geometry, supporting both GeoJSON coordinates arrays and encoded polyline strings.
     */
    private List<GeoCoordinate> parseGeometry(JsonNode geometryNode) {
        if (geometryNode == null || geometryNode.isMissingNode() || geometryNode.isNull()) {
            return Collections.emptyList();
        }

        // Case 1: GeoJSON format: { "type": "LineString", "coordinates": [[lon, lat], ...] }
        if (geometryNode.isObject() && geometryNode.has("coordinates")) {
            JsonNode coordsArray = geometryNode.path("coordinates");
            List<GeoCoordinate> coordinates = new ArrayList<>();
            if (coordsArray.isArray()) {
                for (JsonNode coord : coordsArray) {
                    // GeoJSON format is [longitude, latitude]
                    double lon = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    coordinates.add(new GeoCoordinate(lat, lon));
                }
            }
            return coordinates;
        }

        // Case 2: Encoded Polyline string
        if (geometryNode.isTextual()) {
            String encoded = geometryNode.asText();
            return decodePolyline(encoded);
        }

        return Collections.emptyList();
    }

    /**
     * Decodes an encoded polyline string into a list of GeoCoordinates.
     * Automatically handles both standard 5-digit (1e5) and 6-digit (1e6) precisions.
     */
    public static List<GeoCoordinate> decodePolyline(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Collections.emptyList();
        }

        // Attempt 5-digit precision first
        List<GeoCoordinate> coords = decodePolylineWithFactor(encoded, 1e5);
        if (!coords.isEmpty()) {
            GeoCoordinate first = coords.get(0);
            if (Math.abs(first.latitude()) <= 90.0 && Math.abs(first.longitude()) <= 180.0) {
                return coords;
            }
        }

        // Fallback to 6-digit precision (polyline6)
        return decodePolylineWithFactor(encoded, 1e6);
    }

    private static List<GeoCoordinate> decodePolylineWithFactor(String encoded, double factor) {
        List<GeoCoordinate> coordinates = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                if (index >= len) break;
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                if (index >= len) break;
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latitude = lat / factor;
            double longitude = lng / factor;
            coordinates.add(new GeoCoordinate(latitude, longitude));
        }
        return coordinates;
    }

    /**
     * Sanitizes strings to prevent sensitive API key leakage in logs and exception messages.
     */
    private String sanitize(String message) {
        if (apiKey != null && !apiKey.isBlank() && message != null) {
            return message.replace(apiKey, "[REDACTED]");
        }
        return message;
    }
}
