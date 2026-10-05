package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.aggregates.AStarPathfinder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteCalculatorServiceImplTest {

    @Test
    void shouldBuildRoutePathFromDynamicAisGraph() {
        AStarPathfinder pathfinder = mock(AStarPathfinder.class);
        RouteGraphBuilder graphBuilder = mock(RouteGraphBuilder.class);
        GeoUtils geoUtils = new GeoUtils();
        RouteCalculatorServiceImpl service = new RouteCalculatorServiceImpl(pathfinder, graphBuilder, geoUtils);

        Port tokyo = new Port("TOKYO", "Tokyo", new Coordinates(35.651981, 139.764228), "Asia");
        Port callao = new Port("CALLAO", "Callao", new Coordinates(-12.0564, -77.1319), "America");
        MaritimeNode startNode = MaritimeNode.forPort(tokyo);
        MaritimeNode endNode = MaritimeNode.forPort(callao);
        MaritimeNode pacificWest = seaNode("GFW:north-pacific:test:west", 31.0, 170.0);
        MaritimeNode pacificEast = seaNode("GFW:south-pacific:test:east", 2.0, -112.0);

        RouteGraph graph = new RouteGraph();
        graph.addNode(startNode);
        graph.addNode(endNode);
        graph.addNode(pacificWest);
        graph.addNode(pacificEast);
        graph.addBidirectionalEdge(edge(startNode, pacificWest));
        graph.addBidirectionalEdge(edge(pacificWest, pacificEast));
        graph.addBidirectionalEdge(edge(pacificEast, endNode));

        when(graphBuilder.buildDynamicRouteGraphForPorts(Set.of(), Set.of("TOKYO", "CALLAO"))).thenReturn(graph);
        when(pathfinder.findOptimalRoute(any(), any(), eq(graph)))
                .thenReturn(List.of(startNode, pacificWest, pacificEast, endNode));

        RoutePath routePath = service.calculateOptimalRoute(tokyo, callao, Set.of());

        assertThat(routePath.principalPorts()).containsExactly(tokyo, callao);
        assertThat(routePath.orderedWaypoints()).extracting(MaritimeNode::getId)
                .containsExactly(pacificWest.getId(), pacificEast.getId());
        assertThat(routePath.geometry()).containsExactly(
                tokyo.getCoordinates(),
                pacificWest.getCoordinates(),
                pacificEast.getCoordinates(),
                callao.getCoordinates()
        );
        assertThat(routePath.totalDistanceNm()).isGreaterThan(0.0);

        verify(graphBuilder).buildDynamicRouteGraphForPorts(Set.of(), Set.of("TOKYO", "CALLAO"));
        verify(graphBuilder, never()).buildDynamicRouteGraph(Set.of());
        verify(graphBuilder, never()).buildStaticRouteGraph(Set.of());
    }

    @Test
    void shouldFailExplicitlyWhenGraphEdgeIsUnavailable() {
        AStarPathfinder pathfinder = mock(AStarPathfinder.class);
        RouteGraphBuilder graphBuilder = mock(RouteGraphBuilder.class);
        GeoUtils geoUtils = new GeoUtils();
        RouteCalculatorServiceImpl service = new RouteCalculatorServiceImpl(pathfinder, graphBuilder, geoUtils);

        Port tokyo = new Port("TOKYO", "Tokyo", new Coordinates(35.651981, 139.764228), "Asia");
        Port honolulu = new Port("HONOLULU", "Honolulu", new Coordinates(21.3069, -157.8583), "America");
        MaritimeNode startNode = MaritimeNode.forPort(tokyo);
        MaritimeNode endNode = MaritimeNode.forPort(honolulu);
        MaritimeNode waypoint = seaNode("GFW:north-pacific:test:center", 28.0, -170.0);
        MaritimeNode startConnector = seaNode("GFW:north-pacific:test:start-connector", 34.0, 165.0);
        MaritimeNode endConnector = seaNode("GFW:north-pacific:test:end-connector", 22.0, -160.0);

        RouteGraph graph = new RouteGraph();
        graph.addNode(startNode);
        graph.addNode(endNode);
        graph.addNode(waypoint);
        graph.addNode(startConnector);
        graph.addNode(endConnector);
        graph.addBidirectionalEdge(edge(startNode, startConnector));
        graph.addBidirectionalEdge(edge(endNode, endConnector));

        when(graphBuilder.buildDynamicRouteGraphForPorts(Set.of(), Set.of("TOKYO", "HONOLULU"))).thenReturn(graph);
        when(pathfinder.findOptimalRoute(any(), any(), eq(graph)))
                .thenReturn(List.of(startNode, waypoint, endNode));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.calculateOptimalRoute(tokyo, honolulu, Set.of()))
                .isInstanceOf(org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException.class)
                .hasMessageContaining("falta una arista validada");
    }

    private MaritimeNode seaNode(String id, double latitude, double longitude) {
        return MaritimeNode.seaNode(id, id, MaritimeNodeType.SEA_WAYPOINT, new Coordinates(latitude, longitude));
    }

    private MaritimeEdge edge(MaritimeNode fromNode, MaritimeNode toNode) {
        return new MaritimeEdge(
                fromNode,
                toNode,
                100.0,
                10.0,
                false,
                false,
                false,
                false,
                List.of(fromNode.getCoordinates(), toNode.getCoordinates())
        );
    }
}
