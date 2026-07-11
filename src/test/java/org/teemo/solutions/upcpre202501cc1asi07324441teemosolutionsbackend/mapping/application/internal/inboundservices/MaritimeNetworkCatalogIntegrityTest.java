package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeEdge;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteGraph;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.mappers.PortMapper;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaritimeNetworkCatalogIntegrityTest {
    private static final java.util.Set<String> ACTIVE_CORRIDOR_NODES = java.util.Set.of(
            "PACIFIC_SOUTH_EAST",
            "PACIFIC_TROPICAL_EAST",
            "PANAMA_PACIFIC",
            "PANAMA_BALBOA_INNER",
            "PANAMA_MIRAFLORES_LOCKS",
            "PANAMA_PEDRO_MIGUEL_LOCKS",
            "PANAMA_GAILLARD_CUT_SOUTH",
            "PANAMA_CANAL",
            "PANAMA_GAILLARD_CUT_NORTH",
            "PANAMA_GATUN_LAKE_SOUTH",
            "PANAMA_GATUN_LAKE_CENTER",
            "PANAMA_GATUN_LAKE_NORTH",
            "PANAMA_GATUN_LOCKS",
            "PANAMA_COLON_INNER",
            "PANAMA_ATLANTIC",
            "PANAMA_CARIBBEAN_OUTER",
            "CARIBBEAN_SW",
            "CARIBBEAN_WEST",
            "CARIBBEAN_COLOMBIA",
            "CARIBBEAN",
            "CARIBBEAN_ARC",
            "CARIBBEAN_EAST",
            "LESSER_ANTILLES_OUTER",
            "NORTH_ATLANTIC_WEST",
            "NORTH_ATLANTIC_CENTRAL",
            "NORTH_ATLANTIC_EAST",
            "AZORES_CORRIDOR",
            "AZORES_SOUTH",
            "MADEIRA_APPROACH",
            "PORTUGAL_APPROACH",
            "IBERIA_WEST",
            "GIBRALTAR_WEST",
            "GIBRALTAR_STRAIT",
            "ALBORAN_SEA",
            "WEST_MEDITERRANEAN"
    );

    @Test
    void shouldKeepManualSeaWaypointsOutsideCoveredLandMaskPolygons() {
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();
        MaritimeLandMask landMask = new MaritimeLandMask(new GeoUtils());

        for (MaritimeNode node : catalog.coreNodes()) {
            if (!ACTIVE_CORRIDOR_NODES.contains(node.getId())) {
                continue;
            }
            if (node.getType() != MaritimeNodeType.SEA_WAYPOINT && node.getType() != MaritimeNodeType.STRAIT) {
                continue;
            }
            assertThat(landMask.isOnLand(node.getCoordinates()))
                    .as(node.getId())
                    .isFalse();
        }
    }

    @Test
    void shouldKeepAllManualSeaAndStraitWaypointsOutsideCoveredLandMaskPolygons() {
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();
        MaritimeLandMask landMask = new MaritimeLandMask(new GeoUtils());

        List<String> invalidNodes = catalog.coreNodes().stream()
                .filter(node -> node.getType() == MaritimeNodeType.SEA_WAYPOINT || node.getType() == MaritimeNodeType.STRAIT)
                .filter(node -> landMask.isOnLand(node.getCoordinates()))
                .map(node -> node.getId() + "@" + format(node.getCoordinates()))
                .toList();

        assertThat(invalidNodes)
                .describedAs("Nodos manuales maritimos sobre tierra: %s", invalidNodes)
                .isEmpty();
    }

    @Test
    void shouldBuildCatalogBackboneWithoutLandCrossingSeaEdges() {
        GeoUtils geoUtils = new GeoUtils();
        MaritimeLandMask landMask = new MaritimeLandMask(geoUtils);
        RouteGraph graph = buildCatalogBackboneGraph(geoUtils, landMask);

        List<String> invalidNodes = graph.getAllNodes().stream()
                .filter(node -> node.getType() == MaritimeNodeType.SEA_WAYPOINT || node.getType() == MaritimeNodeType.STRAIT)
                .filter(node -> landMask.isOnLand(node.getCoordinates()))
                .map(node -> node.getId() + "@" + format(node.getCoordinates()))
                .toList();

        Set<String> invalidEdges = graph.getAllNodes().stream()
                .flatMap(node -> graph.getAdjacentEdges(node).stream())
                .filter(edge -> !edge.canal())
                .filter(edge -> landMask.crossesLand(edge.geometry()))
                .map(edge -> edge.fromNode().getId() + "->" + edge.toNode().getId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        assertThat(invalidNodes)
                .describedAs("Nodos maritimos del backbone construido sobre tierra: %s", invalidNodes)
                .isEmpty();
        assertThat(invalidEdges)
                .describedAs("Aristas maritimas del backbone construido que cruzan tierra: %s", invalidEdges)
                .isEmpty();
    }

    @Test
    void shouldKeepNonCanalCoreEdgesOutsideCoveredLandMaskPolygons() {
        MaritimeNetworkCatalog catalog = new MaritimeNetworkCatalog();
        MaritimeLandMask landMask = new MaritimeLandMask(new GeoUtils());

        for (MaritimeNetworkCatalog.EdgeDefinition edge : catalog.coreEdges()) {
            if (!ACTIVE_CORRIDOR_NODES.contains(edge.fromNodeId()) || !ACTIVE_CORRIDOR_NODES.contains(edge.toNodeId())) {
                continue;
            }
            if (edge.canal()) {
                continue;
            }

            MaritimeNode fromNode = catalog.findNode(edge.fromNodeId()).orElseThrow();
            MaritimeNode toNode = catalog.findNode(edge.toNodeId()).orElseThrow();
            java.util.List<Coordinates> geometry = edge.geometry().isEmpty()
                    ? java.util.List.of(fromNode.getCoordinates(), toNode.getCoordinates())
                    : edge.geometry();

            assertThat(landMask.crossesLand(geometry))
                    .as(edge.fromNodeId() + "->" + edge.toNodeId())
                    .isFalse();
        }
    }

    private RouteGraph buildCatalogBackboneGraph(GeoUtils geoUtils, MaritimeLandMask landMask) {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of(
                portDocument("GOTO-ID", "Goto", 32.6953, 128.8419, "Asia"),
                portDocument("SHANGHAI-ID", "Shanghai", 31.2304, 121.4737, "Asia")
        ));

        RouteGraphBuilder builder = new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                geoUtils,
                landMask,
                new MaritimeCorridorOverlayProvider() {
                    @Override
                    public MaritimeCorridorOverlay currentOverlay() {
                        return MaritimeCorridorOverlay.empty("GLOBAL_FISHING_WATCH");
                    }

                    @Override
                    public MaritimeCorridorOverlay refreshOverlay() {
                        return MaritimeCorridorOverlay.empty("GLOBAL_FISHING_WATCH");
                    }
                }
        );

        return builder.buildDynamicRouteGraphForPorts(Set.of(), Set.of("GOTO-ID", "SHANGHAI-ID"));
    }

    private PortDocument portDocument(String id, String name, double latitude, double longitude, String continent) {
        PortDocument document = new PortDocument();
        document.setId(id);
        document.setName(name);
        document.setContinent(continent);
        document.setDisabled(false);
        document.setCoordinates(new PortDocument.CoordinatesDocument(latitude, longitude));
        return document;
    }

    private String format(Coordinates coordinates) {
        return coordinates.latitude() + "," + coordinates.longitude();
    }
}
