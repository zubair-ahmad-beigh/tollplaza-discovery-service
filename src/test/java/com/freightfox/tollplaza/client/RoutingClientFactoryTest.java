package com.freightfox.tollplaza.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingClientFactoryTest {

    @Mock
    private OsrmRoutingClient osrmRoutingClient;

    @Mock
    private MapplsRoutingClient mapplsRoutingClient;

    @Test
    @DisplayName("Should select OSRM strategy when configuredProvider is osrm")
    void shouldSelectOsrmWhenConfigured() {
        RoutingClientFactory factory = new RoutingClientFactory(osrmRoutingClient, mapplsRoutingClient, "osrm");
        assertThat(factory.getActiveClient()).isEqualTo(osrmRoutingClient);
    }

    @Test
    @DisplayName("Should select Mappls when configured and credentials exist")
    void shouldSelectMapplsWhenCredentialsConfigured() {
        when(mapplsRoutingClient.isConfigured()).thenReturn(true);

        RoutingClientFactory factory = new RoutingClientFactory(osrmRoutingClient, mapplsRoutingClient, "mappls");
        assertThat(factory.getActiveClient()).isEqualTo(mapplsRoutingClient);
    }

    @Test
    @DisplayName("Should fallback to OSRM when Mappls requested but credentials missing")
    void shouldFallbackToOsrmWhenMapplsCredentialsMissing() {
        when(mapplsRoutingClient.isConfigured()).thenReturn(false);

        RoutingClientFactory factory = new RoutingClientFactory(osrmRoutingClient, mapplsRoutingClient, "mappls");
        assertThat(factory.getActiveClient()).isEqualTo(osrmRoutingClient);
    }

    @Test
    @DisplayName("Auto strategy should pick Mappls when configured, else OSRM")
    void shouldHandleAutoStrategy() {
        when(mapplsRoutingClient.isConfigured()).thenReturn(false);
        RoutingClientFactory autoWithoutCreds = new RoutingClientFactory(osrmRoutingClient, mapplsRoutingClient, "auto");
        assertThat(autoWithoutCreds.getActiveClient()).isEqualTo(osrmRoutingClient);

        when(mapplsRoutingClient.isConfigured()).thenReturn(true);
        RoutingClientFactory autoWithCreds = new RoutingClientFactory(osrmRoutingClient, mapplsRoutingClient, "auto");
        assertThat(autoWithCreds.getActiveClient()).isEqualTo(mapplsRoutingClient);
    }
}

