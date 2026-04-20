package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.RouteGraphBuilder;

@Service
public class RouteGraphWarmupService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RouteGraphWarmupService.class);

    private final RouteGraphBuilder routeGraphBuilder;

    public RouteGraphWarmupService(RouteGraphBuilder routeGraphBuilder) {
        this.routeGraphBuilder = routeGraphBuilder;
    }

    @Override
    public void run(ApplicationArguments args) {
        prewarmBaseGraphs();
    }

    public void prewarmBaseGraphs() {
        long startedAt = System.nanoTime();
        logger.info("route.graph.warmup.start");
        routeGraphBuilder.prewarmBaseGraphs();
        long elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        logger.info("route.graph.warmup.completed elapsedMs={}", elapsedMs);
    }
}
