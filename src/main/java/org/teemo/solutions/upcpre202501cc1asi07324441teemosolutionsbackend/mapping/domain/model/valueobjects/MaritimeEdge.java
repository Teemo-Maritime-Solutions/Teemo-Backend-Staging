package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects;

import java.util.List;

public record MaritimeEdge(
        MaritimeNode fromNode,
        MaritimeNode toNode,
        double distanceNm,
        double estimatedHours,
        boolean restricted,
        boolean disabled,
        boolean canal,
        boolean highRisk,
        List<Coordinates> geometry
) {
    public MaritimeEdge {
        geometry = geometry == null ? List.of() : List.copyOf(geometry);
    }
}
