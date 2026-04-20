package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchClient;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchProperties;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalFishingWatchCorridorOverlayProviderTest {

    @Test
    void shouldBuildOverlayFromPresenceCells() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();

        GlobalFishingWatchProperties.RegionProperties region = properties.getRegions().get(0);
        when(client.fetchPresenceCells(region)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(14.20, -73.90, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(15.10, -72.20, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(16.40, -70.10, 310.0, 5)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                catalog,
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.source()).isEqualTo("GLOBAL_FISHING_WATCH");
        assertThat(overlay.nodes()).hasSize(3);
        assertThat(overlay.edges()).isNotEmpty();
        assertThat(overlay.edges()).anyMatch(edge -> edge.fromNodeId().startsWith("GFW:") && edge.toNodeId().startsWith("GFW:"));
        assertThat(overlay.edges()).anyMatch(edge -> edge.fromNodeId().startsWith("GFW:") && !edge.toNodeId().startsWith("GFW:")
                || !edge.fromNodeId().startsWith("GFW:") && edge.toNodeId().startsWith("GFW:"));
    }

    @Test
    void currentOverlayShouldReturnCachedOverlayWithoutTriggeringRefresh() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                catalog,
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay current = provider.currentOverlay();

        assertThat(current.refreshedAt()).isNull();
        assertThat(current.isEmpty()).isTrue();
        verify(client, never()).fetchPresenceCells(any());
    }

    @Test
    void shouldReturnWarningWhenDisabled() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = new GlobalFishingWatchProperties();
        properties.setEnabled(false);

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.isEmpty()).isTrue();
        assertThat(overlay.warnings()).isNotEmpty();
    }

    @Test
    void shouldPreferLocalMeshNeighborsOverLongRegionJump() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        properties.setRegions(List.of(new GlobalFishingWatchProperties.RegionProperties(
                "mesh-test",
                "Mesh Test",
                0.0,
                0.0,
                12.0,
                12.0
        )));
        properties.setNearestNeighbors(4);
        properties.setMaxNeighborDistanceNm(1000.0);

        GlobalFishingWatchProperties.RegionProperties region = properties.getRegions().get(0);
        when(client.fetchPresenceCells(region)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(1.0, 1.0, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(2.0, 2.0, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(10.5, 10.5, 310.0, 5)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.edges())
                .noneMatch(edge -> edge.fromNodeId().contains(":1_00:1_00") && edge.toNodeId().contains(":10_50:10_50")
                        || edge.fromNodeId().contains(":10_50:10_50") && edge.toNodeId().contains(":1_00:1_00"));
    }

    @Test
    void shouldNotBridgeAtlanticRegionDirectlyToPanamaPacific() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        properties.setRegions(List.of(new GlobalFishingWatchProperties.RegionProperties(
                "atlantic-north-west",
                "North Atlantic Western Lanes",
                10.0,
                -85.0,
                30.0,
                -60.0
        )));
        properties.setBridgeConnections(5);
        properties.setMaxBridgeDistanceNm(900.0);

        GlobalFishingWatchProperties.RegionProperties region = properties.getRegions().get(0);
        when(client.fetchPresenceCells(region)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(12.3, -72.0, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(12.5, -70.0, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(14.6, -61.1, 310.0, 5)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.edges())
                .noneMatch(edge -> edge.fromNodeId().equals("PANAMA_PACIFIC") || edge.toNodeId().equals("PANAMA_PACIFIC"));
    }

    @Test
    void shouldNotBridgeCaribbeanPanamaRegionToInternalCanalNodes() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        properties.setRegions(List.of(new GlobalFishingWatchProperties.RegionProperties(
                "caribbean-panama-west",
                "Panama and Western Caribbean",
                7.0,
                -81.5,
                14.0,
                -75.0
        )));
        properties.setBridgeConnections(5);
        properties.setMaxBridgeDistanceNm(900.0);

        GlobalFishingWatchProperties.RegionProperties region = properties.getRegions().get(0);
        when(client.fetchPresenceCells(region)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(8.5, -77.0, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(9.1, -79.6, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(10.4, -75.5, 310.0, 5)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.edges())
                .noneMatch(edge -> edge.fromNodeId().equals("PANAMA_CANAL")
                        || edge.toNodeId().equals("PANAMA_CANAL")
                        || edge.fromNodeId().equals("PANAMA_GAILLARD_CUT_SOUTH")
                        || edge.toNodeId().equals("PANAMA_GAILLARD_CUT_SOUTH"));
    }

    @Test
    void shouldRejectOverlayEdgesThatCrossCentralAmericaLandBridge() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        properties.setRegions(List.of(new GlobalFishingWatchProperties.RegionProperties(
                "land-bridge-test",
                "Land Bridge Test",
                8.5,
                -83.2,
                10.5,
                -79.4
        )));
        properties.setNearestNeighbors(2);
        properties.setMaxNeighborDistanceNm(260.0);
        properties.setBridgeConnections(0);

        GlobalFishingWatchProperties.RegionProperties region = properties.getRegions().get(0);
        when(client.fetchPresenceCells(region)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(9.05, -82.75, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(9.20, -79.85, 360.0, 7)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()) {
                    @Override
                    public boolean isOnLand(Coordinates coordinates) {
                        return false;
                    }

                    @Override
                    public boolean crossesLand(Coordinates start, Coordinates end) {
                        return true;
                    }
                }
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.nodes()).hasSize(2);
        assertThat(overlay.edges()).isEmpty();
    }

    @Test
    void shouldNotConnectPeruPacificOverlayDirectlyToCaribbeanOverlay() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        properties.setRegions(List.of(
                new GlobalFishingWatchProperties.RegionProperties(
                        "peru-ecuador-coast",
                        "Peru and Ecuador Coastal Corridor",
                        -14.5,
                        -86.0,
                        6.5,
                        -76.0
                ),
                new GlobalFishingWatchProperties.RegionProperties(
                        "caribbean-panama-west",
                        "Panama and Western Caribbean",
                        7.0,
                        -81.5,
                        14.0,
                        -75.0
                )
        ));
        properties.setNearestNeighbors(3);
        properties.setBridgeConnections(2);
        properties.setMaxNeighborDistanceNm(900.0);
        properties.setMaxBridgeDistanceNm(650.0);

        GlobalFishingWatchProperties.RegionProperties peruRegion = properties.getRegions().get(0);
        GlobalFishingWatchProperties.RegionProperties caribbeanRegion = properties.getRegions().get(1);
        when(client.fetchPresenceCells(peruRegion)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(-12.0, -77.2, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(-4.0, -80.0, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(6.5, -79.6, 310.0, 5)
        ));
        when(client.fetchPresenceCells(caribbeanRegion)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(6.4, -79.5, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(9.6, -78.7, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(12.3, -77.1, 310.0, 5)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils())
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.edges())
                .noneMatch(edge -> edge.fromNodeId().startsWith("GFW:peru-ecuador-coast:")
                        && edge.toNodeId().startsWith("GFW:caribbean-panama-west:")
                        || edge.fromNodeId().startsWith("GFW:caribbean-panama-west:")
                        && edge.toNodeId().startsWith("GFW:peru-ecuador-coast:"));
    }

    private GlobalFishingWatchProperties configuredProperties() {
        GlobalFishingWatchProperties properties = new GlobalFishingWatchProperties();
        properties.setEnabled(true);
        properties.setApiToken("test-token");
        properties.setRegions(List.of(new GlobalFishingWatchProperties.RegionProperties(
                "caribbean-test",
                "Caribbean Test",
                12.0,
                -76.0,
                18.0,
                -68.0
        )));
        properties.setMaxCellsPerRegion(6);
        properties.setNearestNeighbors(2);
        properties.setBridgeConnections(2);
        properties.setMaxNeighborDistanceNm(600.0);
        properties.setMaxBridgeDistanceNm(400.0);
        properties.setMinHours(100.0);
        properties.setMinVesselIds(2);
        return properties;
    }
}
