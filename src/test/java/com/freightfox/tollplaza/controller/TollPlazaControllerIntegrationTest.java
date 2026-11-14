package com.freightfox.tollplaza.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightfox.tollplaza.dto.request.TollPlazaRequest;
import com.freightfox.tollplaza.dto.response.RouteDto;
import com.freightfox.tollplaza.dto.response.TollPlazaDto;
import com.freightfox.tollplaza.dto.response.TollPlazaResponse;
import com.freightfox.tollplaza.exception.PincodeNotFoundException;
import com.freightfox.tollplaza.exception.RoutingException;
import com.freightfox.tollplaza.exception.SameSourceDestinationException;
import com.freightfox.tollplaza.service.TollPlazaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TollPlazaController.class)
class TollPlazaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TollPlazaService tollPlazaService;

    @Test
    @DisplayName("POST /api/v1/toll-plazas - Success 200 OK")
    void shouldReturn200WithTollPlazas() throws Exception {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("411045")
                .build();

        TollPlazaResponse mockResponse = TollPlazaResponse.builder()
                .route(RouteDto.builder()
                        .sourcePincode("560064")
                        .destinationPincode("411045")
                        .distanceInKm(850.5)
                        .build())
                .tollPlazas(List.of(
                        TollPlazaDto.builder()
                                .name("Example Toll Plaza")
                                .latitude(18.50)
                                .longitude(73.80)
                                .distanceFromSource(250.0)
                                .build()
                ))
                .build();

        when(tollPlazaService.findTollPlazasBetweenPincodes(any())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route.sourcePincode", is("560064")))
                .andExpect(jsonPath("$.route.destinationPincode", is("411045")))
                .andExpect(jsonPath("$.route.distanceInKm", is(850.5)))
                .andExpect(jsonPath("$.tollPlazas", hasSize(1)))
                .andExpect(jsonPath("$.tollPlazas[0].name", is("Example Toll Plaza")))
                .andExpect(jsonPath("$.tollPlazas[0].distanceFromSource", is(250.0)));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas - 400 Bad Request for invalid pincode format")
    void shouldReturn400ForInvalidPincodeFormat() throws Exception {
        TollPlazaRequest invalidRequest = TollPlazaRequest.builder()
                .sourcePincode("12345") // Only 5 digits (invalid)
                .destinationPincode("411045")
                .build();

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("sourcePincode must be a valid 6-digit Indian pincode")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas - 404 Not Found when pincode does not exist")
    void shouldReturn404WhenPincodeNotFound() throws Exception {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("999999")
                .destinationPincode("411045")
                .build();

        when(tollPlazaService.findTollPlazasBetweenPincodes(any()))
                .thenThrow(new PincodeNotFoundException("999999"));

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Pincode not found: 999999")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas - 502 Bad Gateway when routing provider fails")
    void shouldReturn502WhenRoutingProviderFails() throws Exception {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("411045")
                .build();

        when(tollPlazaService.findTollPlazasBetweenPincodes(any()))
                .thenThrow(new RoutingException("Routing provider unavailable: Connection timed out"));

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)))
                .andExpect(jsonPath("$.error", is("Bad Gateway")))
                .andExpect(jsonPath("$.message", containsString("Routing provider error: Routing provider unavailable: Connection timed out")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas - 400 Bad Request when source and destination pincodes are identical")
    void shouldReturn400WhenSourceAndDestinationAreIdentical() throws Exception {
        TollPlazaRequest request = TollPlazaRequest.builder()
                .sourcePincode("560064")
                .destinationPincode("560064")
                .build();

        when(tollPlazaService.findTollPlazasBetweenPincodes(any()))
                .thenThrow(new SameSourceDestinationException("Source and destination pincodes cannot be identical"));

        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("Source and destination pincodes cannot be identical")));
    }

    @Test
    @DisplayName("POST /api/v1/toll-plazas - 400 Bad Request when payload is empty JSON")
    void shouldReturn400WhenPayloadIsEmptyJson() throws Exception {
        mockMvc.perform(post("/api/v1/toll-plazas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }
}

