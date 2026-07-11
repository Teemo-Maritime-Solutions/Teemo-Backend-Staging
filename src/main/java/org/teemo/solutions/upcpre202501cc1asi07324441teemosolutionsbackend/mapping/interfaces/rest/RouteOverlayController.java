package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RouteOverlayRefreshService;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteOverlayRefreshResource;

@RestController
@RequestMapping(value = "/api/routes/ais-overlay", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Route", description = "Route Endpoints")
public class RouteOverlayController {

    private final RouteOverlayRefreshService routeOverlayRefreshService;

    public RouteOverlayController(RouteOverlayRefreshService routeOverlayRefreshService) {
        this.routeOverlayRefreshService = routeOverlayRefreshService;
    }

    @Operation(summary = "Obtiene el estado del overlay AIS actual")
    @GetMapping("/status")
    public ResponseEntity<RouteOverlayRefreshResource> currentOverlayStatus() {
        return ResponseEntity.ok(routeOverlayRefreshService.currentOverlayStatus());
    }

    @Operation(summary = "Refresca el overlay AIS y limpia el cache del grafo")
    @PostMapping("/refresh")
    public ResponseEntity<RouteOverlayRefreshResource> refreshOverlay() {
        return ResponseEntity.ok(routeOverlayRefreshService.refreshOverlay());
    }
}
