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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaritimeGraphLandSafetyTest {

    @Test
    void shouldKeepBuiltGraphSeaAndStraitNodesOffLand() {
        GeoUtils geoUtils = new GeoUtils();
        MaritimeLandMask landMask = new MaritimeLandMask(geoUtils);
        RouteGraph graph = buildStaticGraph(geoUtils, landMask);

        List<String> invalidNodes = graph.getAllNodes().stream()
                .filter(this::requiresOpenWaterPlacement)
                .filter(node -> landMask.isOnLand(node.getCoordinates()))
                .map(node -> node.getId() + "@" + format(node.getCoordinates()))
                .toList();

        assertThat(invalidNodes)
                .describedAs("Nodos maritimos sobre tierra: %s", invalidNodes)
                .isEmpty();
    }

    @Test
    void shouldKeepBuiltGraphNonCanalEdgesOffLand() {
        GeoUtils geoUtils = new GeoUtils();
        MaritimeLandMask landMask = new MaritimeLandMask(geoUtils);
        RouteGraph graph = buildStaticGraph(geoUtils, landMask);

        Set<String> invalidEdges = new LinkedHashSet<>();
        for (MaritimeNode fromNode : graph.getAllNodes()) {
            for (MaritimeEdge edge : graph.getAdjacentEdges(fromNode)) {
                if (edge.canal()) {
                    continue;
                }
                if (requiresOpenWaterPlacement(edge.fromNode()) && landMask.isOnLand(edge.fromNode().getCoordinates())) {
                    invalidEdges.add(edge.fromNode().getId() + "->" + edge.toNode().getId());
                    continue;
                }
                if (requiresOpenWaterPlacement(edge.toNode()) && landMask.isOnLand(edge.toNode().getCoordinates())) {
                    invalidEdges.add(edge.fromNode().getId() + "->" + edge.toNode().getId());
                    continue;
                }
                if (landMask.crossesLand(edge.geometry())) {
                    invalidEdges.add(edge.fromNode().getId() + "->" + edge.toNode().getId());
                }
            }
        }

        assertThat(invalidEdges)
                .describedAs("Aristas maritimas no canal que tocan tierra: %s", invalidEdges)
                .isEmpty();
    }

    private RouteGraph buildStaticGraph(GeoUtils geoUtils, MaritimeLandMask landMask) {
        PortRepository portRepository = mock(PortRepository.class);
        when(portRepository.findAll()).thenReturn(List.of());
        return new RouteGraphBuilder(
                portRepository,
                new PortMapper(),
                new MaritimeNetworkCatalog(),
                geoUtils,
                landMask,
                new MaritimeCorridorOverlayProvider() {
                    @Override
                    public MaritimeCorridorOverlay currentOverlay() {
                        return MaritimeCorridorOverlay.empty("STATIC");
                    }

                    @Override
                    public MaritimeCorridorOverlay refreshOverlay() {
                        return MaritimeCorridorOverlay.empty("STATIC");
                    }
                }
        ).buildStaticRouteGraph(Set.of());
    }

    private boolean requiresOpenWaterPlacement(MaritimeNode node) {
        return node.getType() == MaritimeNodeType.SEA_WAYPOINT || node.getType() == MaritimeNodeType.STRAIT;
    }

    private String format(Coordinates coordinates) {
        return coordinates.latitude() + "," + coordinates.longitude();
    }
}
