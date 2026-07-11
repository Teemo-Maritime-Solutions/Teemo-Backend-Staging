package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

import java.util.List;
import java.util.Map;

public record RouteCalculationResource(
        List<String> optimalRoute,
        double totalDistance,
        List<String> warnings,
        Map<String, CoordinatesResource> coordinatesMapping,
        RouteMetadataResource metadata
) {}
