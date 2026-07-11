package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RouteGraph {
    private static final Logger logger = LoggerFactory.getLogger(RouteGraph.class);

    private final Map<String, MaritimeNode> nodesById = new LinkedHashMap<>();
    private final Map<MaritimeNode, List<MaritimeEdge>> adjacencyList = new LinkedHashMap<>();

    public void addNode(MaritimeNode node) {
        MaritimeNode canonical = nodesById.computeIfAbsent(node.getId(), ignored -> node);
        adjacencyList.putIfAbsent(canonical, new ArrayList<>());
    }

    public MaritimeNode addPortNode(Port port) {
        MaritimeNode node = MaritimeNode.forPort(port);
        addNode(node);
        return findNode(node.getId()).orElse(node);
    }

    public void addBidirectionalEdge(MaritimeEdge edge) {
        MaritimeNode fromNode = ensureNode(edge.fromNode());
        MaritimeNode toNode = ensureNode(edge.toNode());

        addDirectedEdge(new MaritimeEdge(
                fromNode,
                toNode,
                edge.distanceNm(),
                edge.estimatedHours(),
                edge.restricted(),
                edge.disabled(),
                edge.canal(),
                edge.highRisk(),
                normalizedGeometry(edge.geometry(), fromNode, toNode)
        ));

        List<Coordinates> reversedGeometry = new ArrayList<>(normalizedGeometry(edge.geometry(), fromNode, toNode));
        Collections.reverse(reversedGeometry);
        addDirectedEdge(new MaritimeEdge(
                toNode,
                fromNode,
                edge.distanceNm(),
                edge.estimatedHours(),
                edge.restricted(),
                edge.disabled(),
                edge.canal(),
                edge.highRisk(),
                reversedGeometry
        ));
    }

    public List<MaritimeEdge> getAdjacentEdges(MaritimeNode node) {
        MaritimeNode canonical = nodesById.get(node.getId());
        if (canonical == null) {
            return List.of();
        }
        return List.copyOf(adjacencyList.getOrDefault(canonical, List.of()));
    }

    public boolean containsNode(MaritimeNode node) {
        return node != null && nodesById.containsKey(node.getId());
    }

    public Optional<MaritimeNode> findNode(String nodeId) {
        return Optional.ofNullable(nodesById.get(nodeId));
    }

    public Optional<MaritimeNode> findPortNode(String portId) {
        return findNode("PORT:%s".formatted(portId));
    }

    public Optional<MaritimeEdge> findEdge(MaritimeNode fromNode, MaritimeNode toNode) {
        if (fromNode == null || toNode == null) {
            return Optional.empty();
        }
        return getAdjacentEdges(fromNode).stream()
                .filter(edge -> edge.toNode().equals(toNode))
                .findFirst();
    }

    public void removeBidirectionalEdge(String firstNodeId, String secondNodeId) {
        removeDirectedEdge(firstNodeId, secondNodeId);
        removeDirectedEdge(secondNodeId, firstNodeId);
    }

    public int getNodeCount() {
        return nodesById.size();
    }

    public int getEdgeCount() {
        int totalDirectedEdges = adjacencyList.values().stream()
                .mapToInt(List::size)
                .sum();
        return totalDirectedEdges / 2;
    }

    public Set<MaritimeNode> getAllNodes() {
        return new LinkedHashSet<>(nodesById.values());
    }

    public void logAllNodes() {
        logger.info("--- RouteGraph Nodes ---");
        if (nodesById.isEmpty()) {
            logger.info("route.graph.empty");
            return;
        }

        nodesById.values().forEach(node ->
                logger.info("route.graph.node id={} type={} portId={} lat={} lon={}",
                        node.getId(),
                        node.getType(),
                        node.getPortId(),
                        node.getCoordinates().latitude(),
                        node.getCoordinates().longitude())
        );
    }

    private MaritimeNode ensureNode(MaritimeNode node) {
        addNode(node);
        return nodesById.get(node.getId());
    }

    private void addDirectedEdge(MaritimeEdge edge) {
        List<MaritimeEdge> edges = adjacencyList.computeIfAbsent(edge.fromNode(), ignored -> new ArrayList<>());
        boolean alreadyPresent = edges.stream().anyMatch(existing ->
                existing.toNode().equals(edge.toNode())
                        && existing.canal() == edge.canal()
                        && existing.highRisk() == edge.highRisk()
                        && existing.restricted() == edge.restricted()
        );
        if (!alreadyPresent) {
            edges.add(edge);
        }
    }

    private void removeDirectedEdge(String fromNodeId, String toNodeId) {
        MaritimeNode fromNode = nodesById.get(fromNodeId);
        if (fromNode == null) {
            return;
        }
        adjacencyList.computeIfPresent(fromNode, (ignored, edges) -> {
            edges.removeIf(edge -> edge.toNode().getId().equals(toNodeId));
            return edges;
        });
    }

    private List<Coordinates> normalizedGeometry(List<Coordinates> geometry, MaritimeNode fromNode, MaritimeNode toNode) {
        if (geometry == null || geometry.isEmpty()) {
            return List.of(fromNode.getCoordinates(), toNode.getCoordinates());
        }
        return List.copyOf(geometry);
    }
}
