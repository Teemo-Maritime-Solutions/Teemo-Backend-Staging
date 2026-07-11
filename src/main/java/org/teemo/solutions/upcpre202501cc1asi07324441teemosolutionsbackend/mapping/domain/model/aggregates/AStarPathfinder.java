package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.aggregates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeEdge;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteGraph;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.NavigationConditionsProvider;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.SafetyValidator;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

@Component
public class AStarPathfinder {
    private static final Logger logger = LoggerFactory.getLogger(AStarPathfinder.class);

    private final SafetyValidator safetyValidator;
    private final NavigationConditionsProvider navConditions;
    private final GeoUtils geoUtils;
    private final Clock clock;

    private record Node(MaritimeNode node, double gScore, double hScore) {
        private double fScore() {
            return gScore + hScore;
        }
    }

    public AStarPathfinder(SafetyValidator safetyValidator,
                           NavigationConditionsProvider navConditions,
                           GeoUtils geoUtils,
                           Clock clock) {
        this.safetyValidator = safetyValidator;
        this.navConditions = navConditions;
        this.geoUtils = geoUtils;
        this.clock = clock;
    }

    public List<MaritimeNode> findOptimalRoute(MaritimeNode start, MaritimeNode end, RouteGraph graph) {
        validateInputs(start, end, graph);
        logger.info("route.pathfinding.start from={} to={}", start.getId(), end.getId());

        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<MaritimeNode, Double> gScore = new HashMap<>();
        Map<MaritimeNode, MaritimeNode> cameFrom = new HashMap<>();

        gScore.put(start, 0.0);
        openSet.add(new Node(start, 0.0, heuristic(start, end)));

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.node().equals(end)) {
                return reconstructPath(cameFrom, end);
            }

            for (MaritimeEdge edge : graph.getAdjacentEdges(current.node())) {
                if (edge.disabled()) {
                    continue;
                }

                MaritimeNode neighbor = edge.toNode();
                double tentativeGScore = current.gScore() + edgeCost(edge);
                if (tentativeGScore >= gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    continue;
                }

                cameFrom.put(neighbor, current.node());
                gScore.put(neighbor, tentativeGScore);
                openSet.add(new Node(neighbor, tentativeGScore, heuristic(neighbor, end)));
            }
        }

        logger.warn("route.pathfinding.not-found from={} to={}", start.getId(), end.getId());
        throw new RouteNotFoundException(start.getName(), end.getName());
    }

    private void validateInputs(MaritimeNode start, MaritimeNode end, RouteGraph graph) {
        if (!graph.containsNode(start)) {
            throw new IllegalArgumentException("El nodo inicial '" + start.getId() + "' no existe en el grafo.");
        }
        if (!graph.containsNode(end)) {
            throw new IllegalArgumentException("El nodo final '" + end.getId() + "' no existe en el grafo.");
        }
    }

    private double edgeCost(MaritimeEdge edge) {
        double penalty = 0.0;
        if (edge.restricted()) {
            penalty += edge.distanceNm() * 0.10;
        }
        if (edge.highRisk()) {
            penalty += edge.distanceNm() * 0.20;
        }
        return edge.distanceNm() + penalty;
    }

    private double heuristic(MaritimeNode current, MaritimeNode target) {
        return geoUtils.calculateHaversineDistanceNm(current.getCoordinates(), target.getCoordinates());
    }

    private List<MaritimeNode> reconstructPath(Map<MaritimeNode, MaritimeNode> cameFrom, MaritimeNode end) {
        LinkedList<MaritimeNode> path = new LinkedList<>();
        MaritimeNode current = end;
        while (current != null) {
            path.addFirst(current);
            current = cameFrom.get(current);
        }
        return List.copyOf(path);
    }
}
