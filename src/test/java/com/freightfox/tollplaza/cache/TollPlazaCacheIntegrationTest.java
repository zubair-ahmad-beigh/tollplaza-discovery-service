package com.freightfox.tollplaza.cache;

import com.freightfox.tollplaza.client.GeocodingClient;
import com.freightfox.tollplaza.client.RoutingClient;
import com.freightfox.tollplaza.dto.request.TollPlazaRequest;
import com.freightfox.tollplaza.dto.response.TollPlazaResponse;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.RouteGeometry;
import com.freightfox.tollplaza.repository.TollPlazaRepository;
import com.freightfox.tollplaza.service.TollPlazaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("dev")
class TollPlazaCacheIntegrationTest {

    @Autowired
    private TollPlazaService tollPlazaService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private GeocodingClient geocodingClient;

    @MockBean
    private RoutingClient routingClient;

    @MockBean
    private TollPlazaRepository tollPlazaRepository;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache("tollPlazasRouteCache");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("Cache verification: second request with same pincodes should hit cache and avoid re-calculating route")
    void shouldCacheResponseOnRepeatedRequests() {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("411045")
                .build();

        GeoCoordinate source = new GeoCoordinate(13.0, 77.0);
        GeoCoordinate dest = new GeoCoordinate(18.5, 73.8);

        when(geocodingClient.geocodePincode("560064")).thenReturn(Optional.of(source));
        when(geocodingClient.geocodePincode("411045")).thenReturn(Optional.of(dest));

        RouteGeometry route = RouteGeometry.builder()
                .totalDistanceInKm(850.0)
                .coordinates(List.of(source, dest))
                .build();
        when(routingClient.calculateRoute(source, dest)).thenReturn(route);
        when(tollPlazaRepository.findWithinBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        // First call - Cache MISS (executes method logic)
        TollPlazaResponse firstResponse = tollPlazaService.findTollPlazasBetweenPincodes(request);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.getRoute().getDistanceInKm()).isEqualTo(850.0);

        // Verify routing client was invoked once
        verify(routingClient, times(1)).calculateRoute(source, dest);

        // Check that item is in cache
        Cache cache = cacheManager.getCache("tollPlazasRouteCache");
        assertThat(cache).isNotNull();
        TollPlazaResponse cachedValue = cache.get("560064:411045", TollPlazaResponse.class);
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue.getRoute().getDistanceInKm()).isEqualTo(850.0);

        // Second call - Cache HIT (should return from cache without calling routingClient again)
        TollPlazaResponse secondResponse = tollPlazaService.findTollPlazasBetweenPincodes(request);
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getRoute().getDistanceInKm()).isEqualTo(850.0);

        // RoutingClient MUST still only have been called once!
        verify(routingClient, times(1)).calculateRoute(source, dest);

        // Verify Caffeine cache statistics
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache) {
            com.github.benmanes.caffeine.cache.stats.CacheStats stats = caffeineCache.stats();
            assertThat(stats.hitCount()).isGreaterThanOrEqualTo(1);
            assertThat(stats.missCount()).isGreaterThanOrEqualTo(1);
        }
    }
}

