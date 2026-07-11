package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchClient;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchProperties;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalFishingWatchCorridorOverlayProviderTest {

    @Test
    void shouldBuildOverlayFromPresenceCellsUsingOnlyAisNodes() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();

        GlobalFishingWatchProperties.RegionProperties region = properties.getRegions().get(0);
        when(client.fetchPresenceCells(region)).thenReturn(List.of(
                new GlobalFishingWatchClient.PresenceCell(14.20, -73.90, 420.0, 9),
                new GlobalFishingWatchClient.PresenceCell(15.10, -72.20, 360.0, 7),
                new GlobalFishingWatchClient.PresenceCell(16.40, -70.10, 310.0, 5)
        ));

        GlobalFishingWatchCorridorOverlayProvider provider = provider(client, properties, new MaritimeLandMask(new GeoUtils()));

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.source()).isEqualTo("GLOBAL_FISHING_WATCH");
        assertThat(overlay.nodes()).hasSize(3);
        assertThat(overlay.nodes()).allMatch(node -> node.getId().startsWith("GFW:"));
        assertThat(overlay.edges()).isNotEmpty();
        assertThat(overlay.edges()).allMatch(edge ->
                edge.fromNodeId().startsWith("GFW:") && edge.toNodeId().startsWith("GFW:"));
    }

    @Test
    void currentOverlayShouldReturnCachedOverlayWithoutTriggeringRefresh() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();

        GlobalFishingWatchCorridorOverlayProvider provider = provider(client, properties, new MaritimeLandMask(new GeoUtils()));

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

        GlobalFishingWatchCorridorOverlayProvider provider = provider(client, properties, new MaritimeLandMask(new GeoUtils()));

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

        GlobalFishingWatchCorridorOverlayProvider provider = provider(client, properties, new MaritimeLandMask(new GeoUtils()));

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.edges())
                .noneMatch(edge -> edge.fromNodeId().contains(":1_00:1_00") && edge.toNodeId().contains(":10_50:10_50")
                        || edge.fromNodeId().contains(":10_50:10_50") && edge.toNodeId().contains(":1_00:1_00"));
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

        MaritimeLandMask landMask = new MaritimeLandMask(new GeoUtils()) {
            @Override
            public boolean isOnLand(Coordinates coordinates) {
                return false;
            }

            @Override
            public boolean crossesLand(Coordinates start, Coordinates end) {
                return true;
            }
        };

        GlobalFishingWatchCorridorOverlayProvider provider = provider(client, properties, landMask);

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

        GlobalFishingWatchCorridorOverlayProvider provider = provider(client, properties, new MaritimeLandMask(new GeoUtils()));

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.edges())
                .noneMatch(edge -> edge.fromNodeId().startsWith("GFW:peru-ecuador-coast:")
                        && edge.toNodeId().startsWith("GFW:caribbean-panama-west:")
                        || edge.fromNodeId().startsWith("GFW:caribbean-panama-west:")
                        && edge.toNodeId().startsWith("GFW:peru-ecuador-coast:"));
    }

    @Test
    void shouldGeneratePortApproachRegionsFromBackendPorts() {
        GlobalFishingWatchClient client = mock(GlobalFishingWatchClient.class);
        GlobalFishingWatchProperties properties = configuredProperties();
        properties.setRegions(List.of(new GlobalFishingWatchProperties.RegionProperties(
                "west-africa-baseline",
                "West Africa Baseline",
                0.0,
                -30.0,
                20.0,
                -5.0
        )));
        properties.setPortApproachRegionGridDegrees(8.0);
        properties.setPortApproachRegionRadiusDegrees(3.0);
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of(
                port("Dakar", 14.7167, -17.4677),
                port("Nuakchot", 18.0731, -15.9582)
        ));
        when(client.fetchPresenceCells(argThat(region -> region.getId().startsWith("port-approach-"))))
                .thenReturn(List.of(
                        new GlobalFishingWatchClient.PresenceCell(14.2, -17.8, 300.0, 4),
                        new GlobalFishingWatchClient.PresenceCell(16.0, -17.0, 260.0, 3)
                ));

        GlobalFishingWatchCorridorOverlayProvider provider = new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                new MaritimeLandMask(new GeoUtils()),
                null,
                portRepository
        );

        MaritimeCorridorOverlay overlay = provider.refreshOverlay();

        assertThat(overlay.nodes())
                .anyMatch(node -> node.getId().startsWith("GFW:port-approach-"));
        verify(client).fetchPresenceCells(argThat(region -> region.getId().startsWith("port-approach-")));
    }

    private GlobalFishingWatchCorridorOverlayProvider provider(GlobalFishingWatchClient client,
                                                               GlobalFishingWatchProperties properties,
                                                               MaritimeLandMask landMask) {
        return new GlobalFishingWatchCorridorOverlayProvider(
                client,
                properties,
                new MaritimeNetworkCatalog(),
                new GeoUtils(),
                landMask
        );
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

    private PortDocument port(String name, double latitude, double longitude) {
        return new PortDocument(
                name,
                new PortDocument.CoordinatesDocument(latitude, longitude),
                "Africa"
        );
    }
}
