package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

import java.time.Instant;

public record RouteCalculationJobStatusResource(
        String jobId,
        String status,
        String message,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        RouteCalculationResource result
) {
}
