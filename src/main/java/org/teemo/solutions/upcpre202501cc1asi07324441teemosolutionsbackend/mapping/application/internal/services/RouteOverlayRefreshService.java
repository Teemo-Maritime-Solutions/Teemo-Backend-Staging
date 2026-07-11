package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlay;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.MaritimeCorridorOverlayProvider;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices.RouteGraphBuilder;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteOverlayRefreshResource;

@Service
public class RouteOverlayRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(RouteOverlayRefreshService.class);

    private final MaritimeCorridorOverlayProvider overlayProvider;
    private final RouteGraphBuilder routeGraphBuilder;

    public RouteOverlayRefreshService(MaritimeCorridorOverlayProvider overlayProvider,
                                      RouteGraphBuilder routeGraphBuilder) {
        this.overlayProvider = overlayProvider;
        this.routeGraphBuilder = routeGraphBuilder;
    }

    public RouteOverlayRefreshResource currentOverlayStatus() {
        return toResource(overlayProvider.currentOverlay(), false);
    }

    public RouteOverlayRefreshResource refreshOverlay() {
        long startedAt = System.nanoTime();
        routeGraphBuilder.invalidateDynamicCache();
        MaritimeCorridorOverlay overlay = overlayProvider.refreshOverlay();
        routeGraphBuilder.invalidateDynamicCache();
        long elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        logger.info("route.overlay.refresh.completed source={} nodes={} edges={} warnings={} elapsedMs={}",
                overlay.source(),
                overlay.nodeCount(),
                overlay.edgeCount(),
                overlay.warnings().size(),
                elapsedMs);
        return toResource(overlay, true);
    }

    private RouteOverlayRefreshResource toResource(MaritimeCorridorOverlay overlay, boolean refreshed) {
        return new RouteOverlayRefreshResource(
                overlay.source(),
                overlay.refreshedAt(),
                overlay.nodeCount(),
                overlay.edgeCount(),
                overlay.warnings(),
                refreshed,
                true
        );
    }
}
