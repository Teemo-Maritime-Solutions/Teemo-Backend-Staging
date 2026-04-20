package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;

import java.util.List;

public record RoutePath(
        List<MaritimeNode> orderedNodes,
        List<Port> principalPorts,
        List<MaritimeNode> orderedWaypoints,
        List<Coordinates> geometry,
        double totalDistanceNm,
        double estimatedHours,
        List<String> warnings
) {
    public RoutePath {
        orderedNodes = List.copyOf(orderedNodes);
        principalPorts = List.copyOf(principalPorts);
        orderedWaypoints = List.copyOf(orderedWaypoints);
        geometry = List.copyOf(geometry);
        warnings = List.copyOf(warnings);
    }
}
