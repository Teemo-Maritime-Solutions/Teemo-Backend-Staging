package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.aggregates.AStarPathfinder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeEdge;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteGraph;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RoutePath;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteCalculatorServiceImplTest {

    @Test
    void shouldFallbackToStaticGraphWhenDynamicOverlayBreaksPanamaAtlanticSegment() {
        AStarPathfinder pathfinder = mock(AStarPathfinder.class);
        RouteGraphBuilder graphBuilder = mock(RouteGraphBuilder.class);
        GeoUtils geoUtils = new GeoUtils();
        RouteCalculatorServiceImpl service = new RouteCalculatorServiceImpl(pathfinder, graphBuilder, geoUtils);

        Port callao = new Port("CALLAO", "Callao", new Coordinates(-12.0564, -77.1319), "America");
        Port lisboa = new Port("LISBOA", "Lisboa", new Coordinates(38.7223, -9.1393), "Europe");

        MaritimeNode startNode = MaritimeNode.forPort(callao);
        MaritimeNode endNode = MaritimeNode.forPort(lisboa);
        MaritimeNode panamaPacificOuter = MaritimeNode.seaNode("PANAMA_PACIFIC_OUTER", "Panama Pacific Outer Approach", MaritimeNodeType.SEA_WAYPOINT, new Coordinates(8.2, -84.0));
        MaritimeNode balboaInner = MaritimeNode.seaNode("PANAMA_BALBOA_INNER", "Balboa Inner Channel", MaritimeNodeType.CANAL, new Coordinates(8.949, -79.566));
        MaritimeNode miraflores = MaritimeNode.seaNode("PANAMA_MIRAFLORES_LOCKS", "Miraflores Locks", MaritimeNodeType.CANAL, new Coordinates(8.995, -79.585));
        MaritimeNode pedroMiguel = MaritimeNode.seaNode("PANAMA_PEDRO_MIGUEL_LOCKS", "Pedro Miguel Locks", MaritimeNodeType.CANAL, new Coordinates(9.036, -79.592));
        MaritimeNode gaillardSouth = MaritimeNode.seaNode("PANAMA_GAILLARD_CUT_SOUTH", "Gaillard Cut South", MaritimeNodeType.CANAL, new Coordinates(9.075, -79.615));
        MaritimeNode canalCenter = MaritimeNode.seaNode("PANAMA_CANAL", "Panama Canal Central Transit", MaritimeNodeType.CANAL, new Coordinates(9.108, -79.648));
        MaritimeNode gaillardNorth = MaritimeNode.seaNode("PANAMA_GAILLARD_CUT_NORTH", "Gaillard Cut North", MaritimeNodeType.CANAL, new Coordinates(9.144, -79.675));
        MaritimeNode gatunSouth = MaritimeNode.seaNode("PANAMA_GATUN_LAKE_SOUTH", "Gatun Lake South", MaritimeNodeType.CANAL, new Coordinates(9.196, -79.726));
        MaritimeNode gatunCenter = MaritimeNode.seaNode("PANAMA_GATUN_LAKE_CENTER", "Gatun Lake Center", MaritimeNodeType.CANAL, new Coordinates(9.247, -79.779));
        MaritimeNode gatunNorth = MaritimeNode.seaNode("PANAMA_GATUN_LAKE_NORTH", "Gatun Lake North", MaritimeNodeType.CANAL, new Coordinates(9.297, -79.842));
        MaritimeNode gatunLocks = MaritimeNode.seaNode("PANAMA_GATUN_LOCKS", "Gatun Locks", MaritimeNodeType.CANAL, new Coordinates(9.334, -79.886));
        MaritimeNode colonInner = MaritimeNode.seaNode("PANAMA_COLON_INNER", "Colon Inner Channel", MaritimeNodeType.CANAL, new Coordinates(9.351, -79.903));
        MaritimeNode panamaAtlantic = MaritimeNode.seaNode("PANAMA_ATLANTIC", "Panama Canal Atlantic Entry", MaritimeNodeType.CANAL, new Coordinates(9.35, -79.92));
        MaritimeNode panamaCaribbeanOuter = MaritimeNode.seaNode("PANAMA_CARIBBEAN_OUTER", "Panama Caribbean Outer Approach", MaritimeNodeType.SEA_WAYPOINT, new Coordinates(10.7, -78.6));

        RouteGraph dynamicGraph = new RouteGraph();
        dynamicGraph.addNode(startNode);
        dynamicGraph.addNode(endNode);
        dynamicGraph.addNode(panamaPacificOuter);
        dynamicGraph.addNode(balboaInner);
        dynamicGraph.addNode(miraflores);
        dynamicGraph.addNode(pedroMiguel);
        dynamicGraph.addNode(gaillardSouth);
        dynamicGraph.addNode(canalCenter);
        dynamicGraph.addNode(gaillardNorth);
        dynamicGraph.addNode(gatunSouth);
        dynamicGraph.addNode(gatunCenter);
        dynamicGraph.addNode(gatunNorth);
        dynamicGraph.addNode(gatunLocks);
        dynamicGraph.addNode(colonInner);
        dynamicGraph.addNode(panamaAtlantic);
        dynamicGraph.addNode(panamaCaribbeanOuter);
        dynamicGraph.addBidirectionalEdge(edge(startNode, panamaPacificOuter, false));
        addPanamaTransit(dynamicGraph, panamaPacificOuter, balboaInner, miraflores, pedroMiguel, gaillardSouth, canalCenter,
                gaillardNorth, gatunSouth, gatunCenter, gatunNorth, gatunLocks, colonInner, panamaAtlantic, panamaCaribbeanOuter);

        RouteGraph staticGraph = new RouteGraph();
        staticGraph.addNode(startNode);
        staticGraph.addNode(endNode);
        staticGraph.addNode(panamaPacificOuter);
        staticGraph.addNode(balboaInner);
        staticGraph.addNode(miraflores);
        staticGraph.addNode(pedroMiguel);
        staticGraph.addNode(gaillardSouth);
        staticGraph.addNode(canalCenter);
        staticGraph.addNode(gaillardNorth);
        staticGraph.addNode(gatunSouth);
        staticGraph.addNode(gatunCenter);
        staticGraph.addNode(gatunNorth);
        staticGraph.addNode(gatunLocks);
        staticGraph.addNode(colonInner);
        staticGraph.addNode(panamaAtlantic);
        staticGraph.addNode(panamaCaribbeanOuter);
        staticGraph.addBidirectionalEdge(edge(startNode, panamaPacificOuter, false));
        addPanamaTransit(staticGraph, panamaPacificOuter, balboaInner, miraflores, pedroMiguel, gaillardSouth, canalCenter,
                gaillardNorth, gatunSouth, gatunCenter, gatunNorth, gatunLocks, colonInner, panamaAtlantic, panamaCaribbeanOuter);
        staticGraph.addBidirectionalEdge(edge(panamaCaribbeanOuter, endNode, false));

        when(graphBuilder.buildDynamicRouteGraph(Set.of())).thenReturn(dynamicGraph);
        when(graphBuilder.buildStaticRouteGraph(Set.of())).thenReturn(staticGraph);

        when(pathfinder.findOptimalRoute(eq(startNode), eq(panamaPacificOuter), eq(dynamicGraph)))
                .thenReturn(List.of(startNode, panamaPacificOuter));
        when(pathfinder.findOptimalRoute(eq(panamaCaribbeanOuter), eq(endNode), eq(dynamicGraph)))
                .thenThrow(new RouteNotFoundException("Panama Caribbean Outer Approach", "Lisboa"));

        when(pathfinder.findOptimalRoute(eq(startNode), eq(panamaPacificOuter), eq(staticGraph)))
                .thenReturn(List.of(startNode, panamaPacificOuter));
        when(pathfinder.findOptimalRoute(eq(panamaCaribbeanOuter), eq(endNode), eq(staticGraph)))
                .thenReturn(List.of(panamaCaribbeanOuter, endNode));

        RoutePath routePath = service.calculateOptimalRoute(callao, lisboa, Set.of());

        assertThat(routePath.principalPorts()).containsExactly(callao, lisboa);
        assertThat(routePath.orderedWaypoints()).extracting(MaritimeNode::getId)
                .containsExactly(
                        "PANAMA_PACIFIC_OUTER",
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
                        "PANAMA_CARIBBEAN_OUTER"
                );
        assertThat(routePath.geometry()).isNotEmpty();
        verify(graphBuilder).buildStaticRouteGraph(Set.of());
    }

    private MaritimeEdge edge(MaritimeNode fromNode, MaritimeNode toNode, boolean canal) {
        return new MaritimeEdge(
                fromNode,
                toNode,
                100.0,
                10.0,
                false,
                false,
                canal,
                false,
                List.of(fromNode.getCoordinates(), toNode.getCoordinates())
        );
    }

    private void addPanamaTransit(RouteGraph graph,
                                  MaritimeNode panamaPacificOuter,
                                  MaritimeNode balboaInner,
                                  MaritimeNode miraflores,
                                  MaritimeNode pedroMiguel,
                                  MaritimeNode gaillardSouth,
                                  MaritimeNode canalCenter,
                                  MaritimeNode gaillardNorth,
                                  MaritimeNode gatunSouth,
                                  MaritimeNode gatunCenter,
                                  MaritimeNode gatunNorth,
                                  MaritimeNode gatunLocks,
                                  MaritimeNode colonInner,
                                  MaritimeNode panamaAtlantic,
                                  MaritimeNode panamaCaribbeanOuter) {
        graph.addBidirectionalEdge(edge(panamaPacificOuter, balboaInner, true));
        graph.addBidirectionalEdge(edge(balboaInner, miraflores, true));
        graph.addBidirectionalEdge(edge(miraflores, pedroMiguel, true));
        graph.addBidirectionalEdge(edge(pedroMiguel, gaillardSouth, true));
        graph.addBidirectionalEdge(edge(gaillardSouth, canalCenter, true));
        graph.addBidirectionalEdge(edge(canalCenter, gaillardNorth, true));
        graph.addBidirectionalEdge(edge(gaillardNorth, gatunSouth, true));
        graph.addBidirectionalEdge(edge(gatunSouth, gatunCenter, true));
        graph.addBidirectionalEdge(edge(gatunCenter, gatunNorth, true));
        graph.addBidirectionalEdge(edge(gatunNorth, gatunLocks, true));
        graph.addBidirectionalEdge(edge(gatunLocks, colonInner, true));
        graph.addBidirectionalEdge(edge(colonInner, panamaAtlantic, true));
        graph.addBidirectionalEdge(edge(panamaAtlantic, panamaCaribbeanOuter, false));
    }
}
