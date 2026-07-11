package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RouteHistoryContext;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RouteService;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RoutePopularityService;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.NoViableRouteAvoidingDisabledPortsException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.PortNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteHistorySource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.RouteDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.RoutePopularityDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteCalculationResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteDistanceResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteMetadataResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteRecalculationResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.PopularRouteResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.shared.application.security.RoutingActorContext;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.shared.application.security.RoutingActorContextProvider;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/routes", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Route", description = "Route Endpoints")
public class RouteController {

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);
    private final RouteService routeService;
    private final RoutePopularityService routePopularityService;
    private final RoutingActorContextProvider actorContextProvider;

    public RouteController(RouteService routeService,
                           RoutePopularityService routePopularityService,
                           RoutingActorContextProvider actorContextProvider) {
        this.routeService = routeService;
        this.routePopularityService = routePopularityService;
        this.actorContextProvider = actorContextProvider;
    }

    @Operation(summary = "Calcula la ruta optima entre dos puertos")
    @PostMapping("/calculate-optimal-route")
    public ResponseEntity<RouteCalculationResource> calculateOptimalRoute(
            @Parameter(description = "ID del puerto de origen", required = true)
            @RequestParam("startPortId") String startPortId,
            @Parameter(description = "ID del puerto de destino", required = true)
            @RequestParam("endPortId") String endPortId) {
        long startedAt = System.nanoTime();
        logger.info("route.api.calculate.start startPortId={} endPortId={}", startPortId, endPortId);
        try {
            RoutingActorContext actor = actorContextProvider.currentActor();
            RouteHistoryContext historyContext = RouteHistoryContext.builder()
                    .userId(actor.userId())
                    .tenantId(actor.tenantId())
                    .source(RouteHistorySource.MANUAL)
                    .metadata(Map.of("endpoint", "/api/routes/calculate-optimal-route"))
                    .build();
            RouteCalculationResource optimalRoute = routeService.calculateOptimalRoute(startPortId, endPortId, historyContext);
            long elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            logger.info("route.api.calculate.completed startPortId={} endPortId={} elapsedMs={} distanceNm={}",
                    startPortId,
                    endPortId,
                    elapsedMs,
                    optimalRoute.totalDistance());
            return ResponseEntity.ok(optimalRoute);
        } catch (PortNotFoundException e) {
            logger.warn("route.api.calculate.port-not-found startPortId={} endPortId={} message={}", startPortId, endPortId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new RouteCalculationResource(
                            List.of(),
                            0.0,
                            List.of("Error: " + e.getMessage()),
                            Map.of(),
                            RouteMetadataResource.empty()
                    ));
        } catch (RouteNotFoundException e) {
            logger.warn("route.api.calculate.not-found startPortId={} endPortId={} message={}", startPortId, endPortId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new RouteCalculationResource(
                            List.of(),
                            0.0,
                            List.of("Ruta no disponible: " + e.getMessage()),
                            Map.of(),
                            RouteMetadataResource.empty()
                    ));
        } catch (Exception e) {
            logger.error("route.api.calculate.failed startPortId={} endPortId={}", startPortId, endPortId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RouteCalculationResource(
                            List.of(),
                            0.0,
                            List.of("Error interno. Revisa el Log interno."),
                            Map.of(),
                            RouteMetadataResource.empty()
                    ));
        }
    }

    @Operation(summary = "Obtiene distancia entre dos puertos")
    @GetMapping("/distance-between-ports")
    public ResponseEntity<RouteDistanceResource> getDistanceBetweenPorts(
            @Parameter(description = "ID del puerto de origen", required = true)
            @RequestParam("startPortId") String startPortId,
            @Parameter(description = "ID del puerto de destino", required = true)
            @RequestParam("endPortId") String endPortId) {
        try {
            RouteCalculationResource optimalRoute = routeService.calculateOptimalRoute(startPortId, endPortId);

            double distance = optimalRoute.totalDistance();
            List<String> messages = optimalRoute.warnings();

            Map<String, Object> meta = Map.of(
                    "startPortId", startPortId,
                    "endPortId", endPortId,
                    "routeSteps", optimalRoute.optimalRoute().size()
            );

            return ResponseEntity.ok(new RouteDistanceResource(distance, messages, meta));

        } catch (PortNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new RouteDistanceResource(
                            0.0,
                            List.of("Error: " + e.getMessage()),
                            Map.of()
                    ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new RouteDistanceResource(
                            0.0,
                            List.of(e.getMessage()),
                            Map.of()
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RouteDistanceResource(
                            0.0,
                            List.of("Error interno: " + e.getMessage()),
                            Map.of()
                    ));
        }
    }

    @Operation(summary = "Recalcula ruta evitando puertos deshabilitados")
    @PreAuthorize("hasAnyRole('ROLE_VIEWER','ROLE_OPERATOR','ROLE_ADMIN')")
    @PostMapping("/{routeId}/recalculate")
    public ResponseEntity<?> recalculateRoute(@PathVariable String routeId) {
        try {
            RoutingActorContext actor = actorContextProvider.currentActor();
            RouteHistoryContext historyContext = RouteHistoryContext.builder()
                    .userId(actor.userId())
                    .tenantId(actor.tenantId())
                    .source(RouteHistorySource.OPERATOR_OVERRIDE)
                    .routeId(routeId)
                    .metadata(Map.of("endpoint", "/api/routes/{routeId}/recalculate"))
                    .build();
            RouteRecalculationResource resource = routeService.recalculateRouteAvoidingDisabledPorts(routeId, historyContext);
            return ResponseEntity.ok(resource);
        } catch (NoViableRouteAvoidingDisabledPortsException ex) {
            Map<String, Object> payload = Map.of(
                    "code", "no_viable_route_avoiding_disabled_ports",
                    "avoidedPortIds", List.copyOf(ex.getAvoidedPortIds()),
                "message", ex.getMessage()
        );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
        }
    }

    @GetMapping("/all-routes")
    public ResponseEntity<List<RouteDocument>> getAllRoutes() {
        List<RouteDocument> routesPage = routeService.findAllRoutes();
        return ResponseEntity.ok(routesPage);
    }

    @Operation(summary = "Obtiene las rutas más populares consultadas por los usuarios")
    @GetMapping("/popular")
    public ResponseEntity<List<PopularRouteResource>> getPopularRoutes(
            @RequestParam(name = "limit", defaultValue = "8") int limit) {
        int sanitizedLimit = limit <= 0 ? 8 : limit;
        List<PopularRouteResource> resources = routePopularityService.findTopRoutes(sanitizedLimit).stream()
                .map(this::toPopularRouteResource)
                .toList();
        return ResponseEntity.ok(resources);
    }

    private PopularRouteResource toPopularRouteResource(RoutePopularityDocument document) {
        return new PopularRouteResource(
                document.getRouteId(),
                document.getOriginPortId(),
                document.getOriginPortName(),
                document.getDestinationPortId(),
                document.getDestinationPortName(),
                document.getSearchesCount()
        );
    }
}
