package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.aggregates.AStarPathfinder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeEdge;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteGraph;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RoutePath;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.RouteCalculatorService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
public class RouteCalculatorServiceImpl implements RouteCalculatorService {

    private final AStarPathfinder pathfinder;
    private final RouteGraphBuilder graphBuilder;
    private final GeoUtils geoUtils;

    public RouteCalculatorServiceImpl(AStarPathfinder pathfinder,
                                      RouteGraphBuilder graphBuilder,
                                      GeoUtils geoUtils) {
        this.pathfinder = pathfinder;
        this.graphBuilder = graphBuilder;
        this.geoUtils = geoUtils;
    }

    @Override
    public RoutePath calculateOptimalRoute(Port start, Port end, Set<String> avoidPortIds) {
        Set<String> routePortIds = java.util.stream.Stream.of(start.getId(), end.getId())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        RouteGraph graph = graphBuilder.buildDynamicRouteGraphForPorts(avoidPortIds, routePortIds);
        if (graph == null) {
            graph = graphBuilder.buildDynamicRouteGraph(avoidPortIds);
        }
        RouteGraph routeGraph = graph;
        MaritimeNode startNode = routeGraph.findPortNode(start.getId()).orElseGet(() -> routeGraph.addPortNode(start));
        MaritimeNode endNode = routeGraph.findPortNode(end.getId()).orElseGet(() -> routeGraph.addPortNode(end));
        validateAisGraphAvailability(routeGraph, startNode, endNode, start, end);
        List<MaritimeNode> orderedNodes = pathfinder.findOptimalRoute(startNode, endNode, routeGraph);
        return toRoutePath(orderedNodes, routeGraph);
    }

    private void validateAisGraphAvailability(RouteGraph graph,
                                              MaritimeNode startNode,
                                              MaritimeNode endNode,
                                              Port start,
                                              Port end) {
        boolean hasOverlayNodes = graph.getAllNodes().stream().anyMatch(node -> !node.isPort());
        if (!hasOverlayNodes) {
            throw new RouteNotFoundException(
                    "No hay corredor AIS disponible para calcular rutas maritimas reales. Verifica routing.maritime.gfw.enabled y GFW_API_TOKEN."
            );
        }
        if (graph.getAdjacentEdges(startNode).isEmpty()) {
            throw new RouteNotFoundException(
                    "El puerto '%s' no pudo conectarse al overlay AIS actual.".formatted(start.getName())
            );
        }
        if (graph.getAdjacentEdges(endNode).isEmpty()) {
            throw new RouteNotFoundException(
                    "El puerto '%s' no pudo conectarse al overlay AIS actual.".formatted(end.getName())
            );
        }
    }

    private RoutePath toRoutePath(List<MaritimeNode> orderedNodes, RouteGraph graph) {
        List<Port> principalPorts = deduplicatePorts(orderedNodes.stream()
                .map(MaritimeNode::getLinkedPort)
                .filter(java.util.Objects::nonNull)
                .toList());

        List<MaritimeNode> orderedWaypoints = orderedNodes.stream()
                .filter(node -> !node.isPort())
                .toList();

        List<Coordinates> geometry = new ArrayList<>();
        double totalDistanceNm = 0.0;
        double estimatedHours = 0.0;

        if (!orderedNodes.isEmpty()) {
            geometry.add(orderedNodes.get(0).getCoordinates());
        }

        for (int index = 0; index < orderedNodes.size() - 1; index++) {
            MaritimeNode fromNode = orderedNodes.get(index);
            MaritimeNode toNode = orderedNodes.get(index + 1);
            MaritimeEdge edge = graph.findEdge(fromNode, toNode).orElseThrow(() ->
                    new RouteNotFoundException("Grafo inconsistente: falta una arista validada entre "
                            + fromNode.getId() + " y " + toNode.getId()));
            totalDistanceNm += edge.distanceNm();
            estimatedHours += edge.estimatedHours();
            appendGeometry(geometry, edge.geometry());
        }

        return new RoutePath(
                List.copyOf(orderedNodes),
                principalPorts,
                List.copyOf(orderedWaypoints),
                List.copyOf(geometry),
                totalDistanceNm,
                estimatedHours,
                List.of()
        );
    }

    private void appendGeometry(List<Coordinates> geometry, List<Coordinates> segment) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        int startIndex = geometry.isEmpty() ? 0 : 1;
        for (int index = startIndex; index < segment.size(); index++) {
            Coordinates point = segment.get(index);
            if (geometry.isEmpty() || !geometry.get(geometry.size() - 1).equals(point)) {
                geometry.add(point);
            }
        }
    }

    private List<Port> deduplicatePorts(List<Port> principalPorts) {
        LinkedHashMap<String, Port> portsById = new LinkedHashMap<>();
        for (Port port : principalPorts) {
            String key = port.getId() != null ? port.getId() : port.getName() + ":" + port.getContinent();
            portsById.putIfAbsent(key, port);
        }
        return List.copyOf(portsById.values());
    }
}
