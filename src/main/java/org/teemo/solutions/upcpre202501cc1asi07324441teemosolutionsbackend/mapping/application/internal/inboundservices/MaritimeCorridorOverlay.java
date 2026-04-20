package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;

import java.time.Instant;
import java.util.List;

public record MaritimeCorridorOverlay(
        String source,
        Instant refreshedAt,
        List<MaritimeNode> nodes,
        List<MaritimeNetworkCatalog.EdgeDefinition> edges,
        List<String> warnings
) {
    public static MaritimeCorridorOverlay empty(String source) {
        return new MaritimeCorridorOverlay(source, null, List.of(), List.of(), List.of());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty() || edges.isEmpty();
    }
}
