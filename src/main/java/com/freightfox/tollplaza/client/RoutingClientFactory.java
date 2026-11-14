package com.freightfox.tollplaza.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Strategy Factory for selecting the active RoutingClient implementation.
 *
 * Selection Rules:
 * - "mappls": Uses Mappls if MAPPLS_API_KEY is configured; falls back to OSRM if missing.
 * - "osrm": Uses Open Source Routing Machine (zero credential dependency).
 * - "auto": Intelligently selects Mappls if API key exists, otherwise OSRM.
 */
@Component
@Primary
@Slf4j
public class RoutingClientFactory implements RoutingClient {

    private final OsrmRoutingClient osrmRoutingClient;
    private final MapplsRoutingClient mapplsRoutingClient;
    private final String configuredProvider;

    public RoutingClientFactory(
            OsrmRoutingClient osrmRoutingClient,
            MapplsRoutingClient mapplsRoutingClient,
            @Value("${app.routing.provider:mappls}") String configuredProvider) {
        this.osrmRoutingClient = osrmRoutingClient;
        this.mapplsRoutingClient = mapplsRoutingClient;
        this.configuredProvider = configuredProvider != null ? configuredProvider.trim().toLowerCase() : "mappls";
    }

    /**
     * Resolves the target RoutingClient strategy based on configuration and credential availability.
     */
    public RoutingClient getActiveClient() {
        switch (configuredProvider) {
            case "mappls":
                if (mapplsRoutingClient.isConfigured()) {
                    log.info("Active routing strategy: Mappls Routing Provider (Static API Key configured)");
                    return mapplsRoutingClient;
                } else {
                    log.warn("Mappls was requested via app.routing.provider=mappls, but MAPPLS_API_KEY is missing. " +
                             "Falling back to OSRM.");
                    return osrmRoutingClient;
                }
            case "auto":
                if (mapplsRoutingClient.isConfigured()) {
                    log.info("Auto strategy: Mappls API key found. Using Mappls.");
                    return mapplsRoutingClient;
                } else {
                    log.info("Auto strategy: Mappls API key not set. Using OSRM.");
                    return osrmRoutingClient;
                }
            case "osrm":
            default:
                log.debug("Active routing strategy: OSRM Routing Provider");
                return osrmRoutingClient;
        }
    }

    @Override
    public com.freightfox.tollplaza.geospatial.RouteGeometry calculateRoute(
            com.freightfox.tollplaza.geospatial.GeoCoordinate source,
            com.freightfox.tollplaza.geospatial.GeoCoordinate destination) {
        return getActiveClient().calculateRoute(source, destination);
    }
}
