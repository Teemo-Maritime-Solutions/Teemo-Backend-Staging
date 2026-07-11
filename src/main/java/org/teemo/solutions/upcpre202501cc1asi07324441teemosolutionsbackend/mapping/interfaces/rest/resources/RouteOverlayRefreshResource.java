package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;

public record RouteOverlayRefreshResource(
        String source,
        Instant refreshedAt,
        int nodeCount,
        int edgeCount,
        List<String> warnings,
        boolean refreshed,
        boolean graphCacheInvalidated
) {
}
