package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

public record MaritimeWaypointResource(
        String id,
        String name,
        double latitude,
        double longitude,
        String type
) {}
