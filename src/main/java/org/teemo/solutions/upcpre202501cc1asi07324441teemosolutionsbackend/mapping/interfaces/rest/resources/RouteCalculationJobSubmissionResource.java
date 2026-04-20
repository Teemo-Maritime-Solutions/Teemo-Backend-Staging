package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources;

public record RouteCalculationJobSubmissionResource(
        String jobId,
        String status,
        String statusUrl,
        String message
) {
}
