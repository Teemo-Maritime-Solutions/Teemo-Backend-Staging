package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

public record MaritimeNetworkSyncResource(
        String source,
        boolean active,
        Instant refreshedAt,
        int nodeCount,
        int edgeCount,
        List<String> warnings
) {
}
