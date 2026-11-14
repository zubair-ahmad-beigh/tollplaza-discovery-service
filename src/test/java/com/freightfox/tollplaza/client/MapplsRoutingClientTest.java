package com.freightfox.tollplaza.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.tollplaza.exception.RoutingException;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.RouteGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapplsRoutingClientTest {

    @Mock
    private RestClient restClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private RestClient.RequestHeadersSpec requestHeadersSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);
    }

    private void mockRestClientChain(String responseBody) {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(responseBody);
    }

    @Test
    @DisplayName("isConfigured should return true only when API key is non-empty")
    void testIsConfigured() {
        MapplsRoutingClient unconfigured = new MapplsRoutingClient(
                restClient, objectMapper, "", "https://apis.mappls.com/advancedmaps/v1");
        assertThat(unconfigured.isConfigured()).isFalse();

        MapplsRoutingClient nullKey = new MapplsRoutingClient(
                restClient, objectMapper, null, "https://apis.mappls.com/advancedmaps/v1");
        assertThat(nullKey.isConfigured()).isFalse();

        MapplsRoutingClient configured = new MapplsRoutingClient(
                restClient, objectMapper, "test-static-api-key", "https://apis.mappls.com/advancedmaps/v1");
        assertThat(configured.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("calculateRoute should throw RoutingException when API key is missing")
    void shouldThrowWhenApiKeyMissing() {
        MapplsRoutingClient unconfigured = new MapplsRoutingClient(
                restClient, objectMapper, "", "https://apis.mappls.com/advancedmaps/v1");

        GeoCoordinate src = new GeoCoordinate(13.1007, 77.5963);
        GeoCoordinate dst = new GeoCoordinate(18.5158, 73.7806);

        assertThatThrownBy(() -> unconfigured.calculateRoute(src, dst))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Mappls Static API Key (MAPPLS_API_KEY) is not configured");
    }

    @Test
    @DisplayName("Should successfully parse Mappls GeoJSON route response and distance")
    void shouldParseMapplsGeoJsonResponse() {
        String mockResponse = """
            {
              "code": "ok",
              "routes": [
                {
                  "distance": 855030.0,
                  "duration": 48200.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [77.596300, 13.100700],
                      [76.953800, 13.611800],
                      [73.780600, 18.515800]
                    ]
                  }
                }
              ]
            }
            """;

        mockRestClientChain(mockResponse);

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "test-key-123", "https://apis.mappls.com/advancedmaps/v1");

        RouteGeometry geometry = client.calculateRoute(
                new GeoCoordinate(13.1007, 77.5963),
                new GeoCoordinate(18.5158, 73.7806));

        assertThat(geometry).isNotNull();
        assertThat(geometry.getTotalDistanceInKm()).isEqualTo(855.03);
        assertThat(geometry.getCoordinates()).hasSize(3);
        assertThat(geometry.getCoordinates().get(0)).isEqualTo(new GeoCoordinate(13.1007, 77.5963));
        assertThat(geometry.getCoordinates().get(2)).isEqualTo(new GeoCoordinate(18.5158, 73.7806));
    }

    @Test
    @DisplayName("Should successfully parse Mappls route response with encoded polyline geometry")
    void shouldParseMapplsEncodedPolylineResponse() {
        // Encoded polyline for (13.1007, 77.5963) and (18.5158, 73.7806)
        // Standard Google polyline encoding (1e5)
        String encodedPolyline = "_p~iF~ps|U_ulLnnqC";

        String mockResponse = """
            {
              "code": "ok",
              "routes": [
                {
                  "distance": 840200.0,
                  "geometry": "%s"
                }
              ]
            }
            """.formatted(encodedPolyline);

        mockRestClientChain(mockResponse);

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "test-key-123", "https://apis.mappls.com/advancedmaps/v1");

        RouteGeometry geometry = client.calculateRoute(
                new GeoCoordinate(13.1007, 77.5963),
                new GeoCoordinate(18.5158, 73.7806));

        assertThat(geometry).isNotNull();
        assertThat(geometry.getTotalDistanceInKm()).isEqualTo(840.2);
        assertThat(geometry.getCoordinates()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Polyline decoder should accurately decode coordinates")
    void testPolylineDecoder() {
        // "_p~iF~ps|U_ulLnnqC" corresponds to (38.5, -120.2) and (40.7, -120.95)
        List<GeoCoordinate> decoded = MapplsRoutingClient.decodePolyline("_p~iF~ps|U_ulLnnqC");
        assertThat(decoded).hasSize(2);
        assertThat(decoded.get(0).latitude()).isCloseTo(38.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(decoded.get(0).longitude()).isCloseTo(-120.2, org.assertj.core.data.Offset.offset(0.001));
        assertThat(decoded.get(1).latitude()).isCloseTo(40.7, org.assertj.core.data.Offset.offset(0.001));
        assertThat(decoded.get(1).longitude()).isCloseTo(-120.95, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Should throw RoutingException on HTTP 401 Unauthorized (Invalid API Key)")
    void shouldHandle401Unauthorized() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(
                HttpClientErrorException.create(HttpStatusCode.valueOf(401), "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "invalid-key-xyz", "https://apis.mappls.com/advancedmaps/v1");

        assertThatThrownBy(() -> client.calculateRoute(new GeoCoordinate(13.0, 77.0), new GeoCoordinate(18.0, 73.0)))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Mappls API key is unauthorized or invalid (HTTP 401)");
    }

    @Test
    @DisplayName("Should throw RoutingException on HTTP 403 Forbidden (Quota Exceeded / Unwhitelisted IP)")
    void shouldHandle403Forbidden() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(
                HttpClientErrorException.create(HttpStatusCode.valueOf(403), "Forbidden", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "quota-exhausted-key", "https://apis.mappls.com/advancedmaps/v1");

        assertThatThrownBy(() -> client.calculateRoute(new GeoCoordinate(13.0, 77.0), new GeoCoordinate(18.0, 73.0)))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Mappls API access forbidden or quota exceeded (HTTP 403)");
    }

    @Test
    @DisplayName("Should throw RoutingException when Mappls returns empty routes array")
    void shouldHandleEmptyRoutes() {
        String mockResponse = """
            {
              "code": "ok",
              "routes": []
            }
            """;

        mockRestClientChain(mockResponse);

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "valid-key", "https://apis.mappls.com/advancedmaps/v1");

        assertThatThrownBy(() -> client.calculateRoute(new GeoCoordinate(13.0, 77.0), new GeoCoordinate(18.0, 73.0)))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("No routes found between source and destination");
    }

    @Test
    @DisplayName("Should throw RoutingException when Mappls returns non-OK service code (e.g. NoRoute)")
    void shouldHandleNoRouteCode() {
        String mockResponse = """
            {
              "code": "NoRoute",
              "message": "No route found between given coordinates"
            }
            """;

        mockRestClientChain(mockResponse);

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "valid-key", "https://apis.mappls.com/advancedmaps/v1");

        assertThatThrownBy(() -> client.calculateRoute(new GeoCoordinate(13.0, 77.0), new GeoCoordinate(18.0, 73.0)))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Mappls route calculation error: No route found between given coordinates (code: NoRoute)");
    }

    @Test
    @DisplayName("Should sanitize and never leak API key in exception messages")
    void shouldSanitizeApiKeyInException() {
        String secretKey = "super-secret-mappls-key-999";
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("Connection failed to https://apis.mappls.com/advancedmaps/v1/" + secretKey + "/route_adv"));

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, secretKey, "https://apis.mappls.com/advancedmaps/v1");

        assertThatThrownBy(() -> client.calculateRoute(new GeoCoordinate(13.0, 77.0), new GeoCoordinate(18.0, 73.0)))
                .isInstanceOf(RoutingException.class)
                .hasMessageNotContaining(secretKey)
                .hasMessageContaining("[REDACTED]");
    }

    @Test
    @DisplayName("Should throw RoutingException explaining IP whitelisting when Mappls returns IP/Domain validation failed")
    void shouldHandle401IpDomainValidationFailed() {
        byte[] body = "{\"msg\":\"IP/Domain validation failed. If you see this often please contact MapmyIndia support at apisupport@mapmyindia.com\",\"error\":\"Api Access Denied\"}".getBytes(StandardCharsets.UTF_8);
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(
                HttpClientErrorException.create(HttpStatusCode.valueOf(401), "Unauthorized", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8));

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "valid-key", "https://route.mappls.com/route/direction");

        assertThatThrownBy(() -> client.calculateRoute(new GeoCoordinate(13.0, 77.0), new GeoCoordinate(18.0, 73.0)))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("IP/Domain validation failed. Please check IP whitelisting in Mappls console");
    }

    @Test
    @DisplayName("Should format official Mappls route_adv URL with access_token query param")
    void shouldFormatOfficialMapplsRouteUrl() {
        String mockResponse = """
            {
              "code": "ok",
              "routes": [
                {
                  "distance": 842000.0,
                  "duration": 47000.0,
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [77.596300, 13.100700],
                      [73.780600, 18.515800]
                    ]
                  }
                }
              ]
            }
            """;
        mockRestClientChain(mockResponse);

        MapplsRoutingClient client = new MapplsRoutingClient(
                restClient, objectMapper, "my-test-key", "https://route.mappls.com/route/direction", "157.48.9.232");

        RouteGeometry route = client.calculateRoute(new GeoCoordinate(13.1007, 77.5963), new GeoCoordinate(18.5158, 73.7806));
        assertThat(route.getTotalDistanceInKm()).isEqualTo(842.0);
    }
}
