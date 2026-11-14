package com.freightfox.tollplaza.service;

import com.freightfox.tollplaza.client.GeocodingClient;
import com.freightfox.tollplaza.client.RoutingClient;
import com.freightfox.tollplaza.dto.request.TollPlazaRequest;
import com.freightfox.tollplaza.dto.response.TollPlazaDto;
import com.freightfox.tollplaza.dto.response.TollPlazaResponse;
import com.freightfox.tollplaza.entity.TollPlaza;
import com.freightfox.tollplaza.exception.PincodeNotFoundException;
import com.freightfox.tollplaza.exception.RoutingException;
import com.freightfox.tollplaza.exception.SameSourceDestinationException;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.RouteGeometry;
import com.freightfox.tollplaza.repository.TollPlazaRepository;
import com.freightfox.tollplaza.service.impl.TollPlazaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TollPlazaServiceImplTest {

    @Mock
    private GeocodingClient geocodingClient;

    @Mock
    private RoutingClient routingClient;

    @Mock
    private TollPlazaRepository tollPlazaRepository;

    private TollPlazaServiceImpl tollPlazaService;

    @BeforeEach
    void setUp() {
        tollPlazaService = new TollPlazaServiceImpl(geocodingClient, routingClient, tollPlazaRepository);
        ReflectionTestUtils.setField(tollPlazaService, "corridorThresholdMeters", 500.0);
    }

    @Test
    @DisplayName("Should throw SameSourceDestinationException when source and destination are identical")
    void shouldRejectIdenticalPincodes() {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("560064")
                .build();

        assertThatThrownBy(() -> tollPlazaService.findTollPlazasBetweenPincodes(request))
                .isInstanceOf(SameSourceDestinationException.class)
                .hasMessageContaining("cannot be identical");
    }

    @Test
    @DisplayName("Should throw PincodeNotFoundException when source pincode cannot be resolved")
    void shouldThrowWhenSourcePincodeNotFound() {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("999999")
                .destinationPincode("411045")
                .build();

        when(geocodingClient.geocodePincode("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tollPlazaService.findTollPlazasBetweenPincodes(request))
                .isInstanceOf(PincodeNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    @DisplayName("Should return correctly ordered and filtered toll plazas along route")
    void shouldReturnOrderedTollPlazasAlongRoute() {
        // Arrange
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("411045")
                .build();

        GeoCoordinate sourceCoord = new GeoCoordinate(13.0, 77.0);
        GeoCoordinate destCoord = new GeoCoordinate(15.0, 77.0);

        when(geocodingClient.geocodePincode("560064")).thenReturn(Optional.of(sourceCoord));
        when(geocodingClient.geocodePincode("411045")).thenReturn(Optional.of(destCoord));

        // 3-point straight route: (13.0, 77.0) -> (14.0, 77.0) -> (15.0, 77.0)
        RouteGeometry routeGeometry = RouteGeometry.builder()
                .totalDistanceInKm(222.0)
                .coordinates(List.of(
                        new GeoCoordinate(13.0, 77.0),
                        new GeoCoordinate(14.0, 77.0),
                        new GeoCoordinate(15.0, 77.0)
                ))
                .build();

        when(routingClient.calculateRoute(sourceCoord, destCoord)).thenReturn(routeGeometry);

        // Toll 1: at (14.5, 77.0) ~ 166.5 km from source
        TollPlaza tollFar = TollPlaza.builder()
                .id(1L)
                .name("Later Toll Plaza")
                .latitude(14.5)
                .longitude(77.0)
                .state("State 2")
                .build();

        // Toll 2: at (13.3, 77.0) ~ 33.3 km from source
        TollPlaza tollNear = TollPlaza.builder()
                .id(2L)
                .name("Earlier Toll Plaza")
                .latitude(13.3)
                .longitude(77.0)
                .state("State 1")
                .build();

        // Toll 3: at (13.5, 77.5) ~ 50 km off the route (outside 500m corridor)
        TollPlaza tollOffRoute = TollPlaza.builder()
                .id(3L)
                .name("Off-Route Toll Plaza")
                .latitude(13.5)
                .longitude(77.5)
                .state("State 1")
                .build();

        when(tollPlazaRepository.findWithinBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(tollFar, tollNear, tollOffRoute));

        // Act
        TollPlazaResponse response = tollPlazaService.findTollPlazasBetweenPincodes(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRoute().getSourcePincode()).isEqualTo("560064");
        assertThat(response.getRoute().getDestinationPincode()).isEqualTo("411045");
        assertThat(response.getRoute().getDistanceInKm()).isEqualTo(222.0);

        // Exactly 2 tolls within corridor, ordered by distance from source
        assertThat(response.getTollPlazas()).hasSize(2);
        assertThat(response.getTollPlazas().get(0).getName()).isEqualTo("Earlier Toll Plaza");
        assertThat(response.getTollPlazas().get(1).getName()).isEqualTo("Later Toll Plaza");

        // Distance from source should be monotonically increasing
        assertThat(response.getTollPlazas().get(0).getDistanceFromSource())
                .isLessThan(response.getTollPlazas().get(1).getDistanceFromSource());
    }

    @Test
    @DisplayName("Should throw RoutingException when routing client fails or returns empty route")
    void shouldThrowWhenRoutingFails() {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("411045")
                .build();

        when(geocodingClient.geocodePincode("560064")).thenReturn(Optional.of(new GeoCoordinate(13.0, 77.0)));
        when(geocodingClient.geocodePincode("411045")).thenReturn(Optional.of(new GeoCoordinate(18.0, 73.0)));

        when(routingClient.calculateRoute(any(), any()))
                .thenThrow(new RoutingException("Connection timeout"));

        assertThatThrownBy(() -> tollPlazaService.findTollPlazasBetweenPincodes(request))
                .isInstanceOf(RoutingException.class)
                .hasMessageContaining("Connection timeout");
    }
}

