package com.freightfox.tollplaza.service.impl;

import com.freightfox.tollplaza.client.GeocodingClient;
import com.freightfox.tollplaza.client.RoutingClient;
import com.freightfox.tollplaza.dto.request.TollPlazaRequest;
import com.freightfox.tollplaza.dto.response.RouteDto;
import com.freightfox.tollplaza.dto.response.TollPlazaDto;
import com.freightfox.tollplaza.dto.response.TollPlazaResponse;
import com.freightfox.tollplaza.entity.TollPlaza;
import com.freightfox.tollplaza.exception.InvalidPincodeException;
import com.freightfox.tollplaza.exception.PincodeNotFoundException;
import com.freightfox.tollplaza.exception.RoutingException;
import com.freightfox.tollplaza.exception.SameSourceDestinationException;
import com.freightfox.tollplaza.geospatial.GeoCoordinate;
import com.freightfox.tollplaza.geospatial.GeospatialMath;
import com.freightfox.tollplaza.geospatial.RouteGeometry;
import com.freightfox.tollplaza.repository.TollPlazaRepository;
import com.freightfox.tollplaza.service.TollPlazaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Core implementation of the TollPlazaService.
 * Coordinates geocoding, driving route generation, spatial database filtering,
 * and polyline projection math.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TollPlazaServiceImpl implements TollPlazaService {

    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;
    private final TollPlazaRepository tollPlazaRepository;

    @Value("${app.geospatial.corridor-threshold-meters:500.0}")
    private double corridorThresholdMeters;

    // Safety bounding box expansion in degrees (~5.5 km buffer)
    private static final double BOUNDING_BOX_BUFFER_DEGREES = 0.05;

    @Override
    @Cacheable(value = "tollPlazasRouteCache", key = "#request.sourcePincode + ':' + #request.destinationPincode")
    @Transactional(readOnly = true)
    public TollPlazaResponse findTollPlazasBetweenPincodes(TollPlazaRequest request) {
        log.info("Processing toll plaza search request: source={}, destination={}",
                request.getSourcePincode(), request.getDestinationPincode());

        // 1. Validation: Ensure source and destination are not identical
        validatePincodes(request.getSourcePincode(), request.getDestinationPincode());

        // 2. Geocode source pincode
        GeoCoordinate sourceCoord = geocodingClient.geocodePincode(request.getSourcePincode())
                .orElseThrow(() -> new PincodeNotFoundException(request.getSourcePincode()));

        // 3. Geocode destination pincode
        GeoCoordinate destCoord = geocodingClient.geocodePincode(request.getDestinationPincode())
                .orElseThrow(() -> new PincodeNotFoundException(request.getDestinationPincode()));

        log.info("Resolved coordinates: source ({}) -> ({}, {}), destination ({}) -> ({}, {})",
                request.getSourcePincode(), sourceCoord.latitude(), sourceCoord.longitude(),
                request.getDestinationPincode(), destCoord.latitude(), destCoord.longitude());

        // 4. Calculate driving route from routing provider
        RouteGeometry routeGeometry = routingClient.calculateRoute(sourceCoord, destCoord);
        if (routeGeometry == null || routeGeometry.isEmpty()) {
            throw new RoutingException("Failed to obtain route geometry between coordinates");
        }

        // 5. Compute route bounding box with safety buffer
        double minLat = routeGeometry.getMinLatitude() - BOUNDING_BOX_BUFFER_DEGREES;
        double maxLat = routeGeometry.getMaxLatitude() + BOUNDING_BOX_BUFFER_DEGREES;
        double minLon = routeGeometry.getMinLongitude() - BOUNDING_BOX_BUFFER_DEGREES;
        double maxLon = routeGeometry.getMaxLongitude() + BOUNDING_BOX_BUFFER_DEGREES;

        // 6. Database spatial pre-filter
        List<TollPlaza> candidateTolls = tollPlazaRepository.findWithinBoundingBox(minLat, maxLat, minLon, maxLon);
        log.info("Bounding box [lat: {} to {}, lon: {} to {}] yielded {} candidate toll plazas",
                minLat, maxLat, minLon, maxLon, candidateTolls.size());

        // 7. Perform fine-grained polyline projection and corridor filtering
        List<TollPlazaDto> matchedTolls = new ArrayList<>();
        Set<String> processedTollNames = new HashSet<>();

        for (TollPlaza candidate : candidateTolls) {
            GeospatialMath.ProjectionResult projection = GeospatialMath.projectPointOntoPolyline(
                    candidate.getLatitude(),
                    candidate.getLongitude(),
                    routeGeometry.getCoordinates(),
                    corridorThresholdMeters
            );

            if (projection.isWithinCorridor()) {
                // Deduplicate if multiple booths share the same standardized name
                String normalizedName = candidate.getName().trim().toLowerCase();
                if (!processedTollNames.contains(normalizedName)) {
                    processedTollNames.add(normalizedName);

                    TollPlazaDto dto = TollPlazaDto.builder()
                            .name(candidate.getName())
                            .latitude(candidate.getLatitude())
                            .longitude(candidate.getLongitude())
                            .distanceFromSource(projection.distanceFromSourceKm())
                            .build();

                    matchedTolls.add(dto);
                }
            }
        }

        // 8. Sort toll plazas strictly by travel distance from the source
        matchedTolls.sort(Comparator.comparingDouble(TollPlazaDto::getDistanceFromSource));

        log.info("Found {} toll plazas along route from {} to {} (total route distance: {} km)",
                matchedTolls.size(), request.getSourcePincode(), request.getDestinationPincode(),
                routeGeometry.getTotalDistanceInKm());

        // 9. Assemble and return final response
        RouteDto routeDto = RouteDto.builder()
                .sourcePincode(request.getSourcePincode())
                .destinationPincode(request.getDestinationPincode())
                .distanceInKm(routeGeometry.getTotalDistanceInKm())
                .build();

        return TollPlazaResponse.builder()
                .route(routeDto)
                .tollPlazas(matchedTolls)
                .build();
    }

    private void validatePincodes(String source, String dest) {
        if (source == null || dest == null) {
            throw new InvalidPincodeException("Pincodes cannot be null");
        }
        if (source.trim().equalsIgnoreCase(dest.trim())) {
            throw new SameSourceDestinationException("Source and destination pincodes cannot be identical");
        }
    }
}

