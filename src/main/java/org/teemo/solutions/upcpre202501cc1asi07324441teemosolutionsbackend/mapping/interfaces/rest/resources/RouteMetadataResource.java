package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

import java.util.List;

public record RouteMetadataResource(
        List<String> portIds,
        List<String> requestedViaPortIds,
        List<String> appliedViaPortIds,
        List<MaritimeWaypointResource> waypoints,
        List<List<Double>> geometry,
        double estimatedHours,
        String algorithm,
        String graphMode
) {
    public static RouteMetadataResource empty() {
        return new RouteMetadataResource(List.of(), List.of(), List.of(), List.of(), List.of(), 0.0, "A_STAR", "PORTS_ONLY");
    }
}
