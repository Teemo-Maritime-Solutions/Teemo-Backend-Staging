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
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaritimeGraphLandSafetyTest {

    @Test
    void shouldKeepBuiltAisGraphSeaNodesOffLand() {
        GeoUtils geoUtils = new GeoUtils();
        MaritimeLandMask landMask = new MaritimeLandMask(geoUtils);
        RouteGraph graph = buildOverlayGraph(geoUtils, landMask);

        List<String> invalidNodes = graph.getAllNodes().stream()
                .filter(node -> node.getType() == MaritimeNodeType.SEA_WAYPOINT || node.getType() == MaritimeNodeType.STRAIT)
                .filter(node -> landMask.isOnLand(node.getCoordinates()))
                .map(node -> node.getId() + "@" + format(node.getCoordinates()))
                .toList();

        assertThat(invalidNodes)
                .describedAs("Nodos AIS sobre tierra: %s", invalidNodes)
                .isEmpty();
    }

    @Test
    void shouldKeepBuiltAisGraphEdgesOffLand() {
        GeoUtils geoUtils = new GeoUtils();
        MaritimeLandMask landMask = new MaritimeLandMask(geoUtils);
        RouteGraph graph = buildOverlayGraph(geoUtils, landMask);

        Set<String> invalidEdges = new LinkedHashSet<>();
        for (MaritimeNode fromNode : graph.getAllNodes()) {
            for (MaritimeEdge edge : graph.getAdjacentEdges(fromNode)) {
                if (landMask.crossesLand(edge.geometry())) {
                    invalidEdges.add(edge.fromNode().getId() + "->" + edge.toNode().getId());
                }
            }
        }

        assertThat(invalidEdges)
                .describedAs("Aristas AIS que tocan tierra: %s", invalidEdges)
                .isEmpty();
    }

    private RouteGraph buildOverlayGraph(GeoUtils geoUtils, MaritimeLandMask landMask) {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());

        MaritimeNode west = MaritimeNode.seaNode(
                "GFW:safety:test:west",
                "GFW Safety West",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(14.0, -145.0)
        );
        MaritimeNode central = MaritimeNode.seaNode(
                "GFW:safety:test:central",
                "GFW Safety Central",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(8.0, -122.0)
        );
        MaritimeNode east = MaritimeNode.seaNode(
                "GFW:safety:test:east",
                "GFW Safety East",
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(-2.0, -101.0)
        );

        MaritimeCorridorOverlay overlay = new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                Instant.parse("2026-04-21T12:00:00Z"),
                List.of(west, central, east),
                List.of(
                        new MaritimeNetworkCatalog.EdgeDefinition(west.getId(), central.getId(), false, false, false),
                        new MaritimeNetworkCatalog.EdgeDefinition(central.getId(), east.getId(), false, false, false)
                ),
                List.of()
        );

        return new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                geoUtils,
                landMask,
                new MaritimeCorridorOverlayProvider() {
                    @Override
                    public MaritimeCorridorOverlay currentOverlay() {
                        return overlay;
                    }

                    @Override
                    public MaritimeCorridorOverlay refreshOverlay() {
                        return overlay;
                    }
                }
        ).buildDynamicRouteGraph(Set.of());
    }

    private String format(Coordinates coordinates) {
        return coordinates.latitude() + "," + coordinates.longitude();
    }
}
