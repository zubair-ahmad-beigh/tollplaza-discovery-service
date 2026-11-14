package com.freightfox.tollplaza.controller;

import com.freightfox.tollplaza.dto.request.TollPlazaRequest;
import com.freightfox.tollplaza.dto.response.ErrorResponse;
import com.freightfox.tollplaza.dto.response.TollPlazaResponse;
import com.freightfox.tollplaza.service.TollPlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing the toll plazas route lookup endpoint.
 */
@RestController
@RequestMapping("/api/v1/toll-plazas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Toll Plazas", description = "Endpoints for finding highway toll plazas along driving routes")
public class TollPlazaController {

    private final TollPlazaService tollPlazaService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Find toll plazas between two Indian pincodes",
            description = "Calculates the driving route between source and destination pincodes and returns all toll plazas encountered in travel sequence."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successful route and toll calculation",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TollPlazaResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                  "route": {
                                    "sourcePincode": "560064",
                                    "destinationPincode": "411045",
                                    "distanceInKm": 850.5
                                  },
                                  "tollPlazas": [
                                    {
                                      "name": "Navalgund Toll Plaza",
                                      "latitude": 15.54,
                                      "longitude": 75.36,
                                      "distanceFromSource": 412.3
                                    }
                                  ]
                                }
                                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request payload or identical source and destination pincodes",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2026-11-12T19:45:00",
                                  "status": 400,
                                  "error": "Bad Request",
                                  "message": "Source and destination pincodes cannot be identical",
                                  "path": "/api/v1/toll-plazas"
                                }
                                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Pincode not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2026-11-12T19:45:00",
                                  "status": 404,
                                  "error": "Not Found",
                                  "message": "Pincode not found: 999999",
                                  "path": "/api/v1/toll-plazas"
                                }
                                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Routing provider or upstream service failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                {
                                  "timestamp": "2026-11-13T14:30:00",
                                  "status": 502,
                                  "error": "Bad Gateway",
                                  "message": "Routing provider error: Routing provider unavailable: Connect timed out",
                                  "path": "/api/v1/toll-plazas"
                                }
                                """)
                    )
            )
    })
    public ResponseEntity<TollPlazaResponse> findTollPlazas(
            @Valid @RequestBody TollPlazaRequest request) {

        log.info("Received REST POST /api/v1/toll-plazas: source={}, destination={}",
                request.getSourcePincode(), request.getDestinationPincode());

        TollPlazaResponse response = tollPlazaService.findTollPlazasBetweenPincodes(request);
        return ResponseEntity.ok(response);
    }
}

