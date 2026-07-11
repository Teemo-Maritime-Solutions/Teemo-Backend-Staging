package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

import java.util.List;

public record RouteRecalculationResource(
        String routeId,
        List<String> optimalRoute,
        boolean recalculated,
        List<String> avoidedPortIds,
        RouteMetadataResource metadata
) {}
