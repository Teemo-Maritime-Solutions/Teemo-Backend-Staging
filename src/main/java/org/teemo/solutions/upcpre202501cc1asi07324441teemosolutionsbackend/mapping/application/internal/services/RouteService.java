// Location: org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.NoViableRouteAvoidingDisabledPortsException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.PortNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.exceptions.RouteNotFoundException;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteHistorySource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteHistoryStatus;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RoutePath;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.RouteCalculatorService;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services.SafetyValidator;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.mappers.PortMapper;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.RouteDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.RouteRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.CoordinatesResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.MaritimeWaypointResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteCalculationResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteMetadataResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteRecalculationResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RouteHistoryContext;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RouteHistoryPersistRequest;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services.RouteHistoryService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {

    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);

    private final RouteRepository routeRepository;
    private final PortRepository portRepository;
    private final RouteCalculatorService routeCalculatorService;
    private final SafetyValidator safetyValidator;
    private final GeoUtils geoUtils;
    private final PortMapper portMapper;
    private final RouteHistoryService routeHistoryService;
    private final RoutePopularityService routePopularityService;
    private final ConcurrentHashMap<String, CompletableFuture<RouteCalculationResource>> inFlightCalculations = new ConcurrentHashMap<>();

    public void saveAllRoutes(List<RouteDocument> routes) { routeRepository.saveAll(routes); }
    public boolean existsByHomePortAndDestinationPort(String h, String d) { return routeRepository.existsByHomePortAndDestinationPort(h, d); }
    public void deleteAllRoutes() { routeRepository.deleteAll(); }
    public List<RouteDocument> findAllRoutes() { return routeRepository.findAll(); }

    public RouteCalculationResource calculateOptimalRoute(String startPortId, String endPortId) {
        return calculateOptimalRoute(startPortId, endPortId, List.of(), false, Collections.emptySet(), null);
    }

    public RouteCalculationResource calculateOptimalRoute(String startPortId, String endPortId, RouteHistoryContext historyContext) {
        return calculateOptimalRoute(startPortId, endPortId, List.of(), false, Collections.emptySet(), historyContext);
    }

    public RouteCalculationResource calculateOptimalRoute(String startPortId, String endPortId, Set<String> avoidPortIds) {
        return calculateOptimalRoute(startPortId, endPortId, List.of(), false, avoidPortIds, null);
    }

    public RouteCalculationResource calculateOptimalRoute(String startPortId, String endPortId, Set<String> avoidPortIds,
                                                          RouteHistoryContext historyContext) {
        return calculateOptimalRoute(startPortId, endPortId, List.of(), false, avoidPortIds, historyContext);
    }

    public RouteCalculationResource calculateOptimalRoute(String startPortId,
                                                          String endPortId,
                                                          List<String> requestedViaPortIds,
                                                          boolean enforceMandatoryViaPorts,
                                                          Set<String> avoidPortIds,
                                                          RouteHistoryContext historyContext) {
        if (historyContext == null) {
            return deduplicatedCalculation(startPortId, endPortId, requestedViaPortIds, enforceMandatoryViaPorts, avoidPortIds);
        }

        return calculateOptimalRouteInternal(
                startPortId,
                endPortId,
                requestedViaPortIds,
                enforceMandatoryViaPorts,
                avoidPortIds,
                historyContext
        );
    }

    private RouteCalculationResource deduplicatedCalculation(String startPortId,
                                                             String endPortId,
                                                             List<String> requestedViaPortIds,
                                                             boolean enforceMandatoryViaPorts,
                                                             Set<String> avoidPortIds) {
        String key = buildCalculationKey(startPortId, endPortId, requestedViaPortIds, enforceMandatoryViaPorts, avoidPortIds);
        CompletableFuture<RouteCalculationResource> future = inFlightCalculations.computeIfAbsent(key, ignored ->
                CompletableFuture.supplyAsync(() -> calculateOptimalRouteInternal(
                        startPortId,
                        endPortId,
                        requestedViaPortIds,
                        enforceMandatoryViaPorts,
                        avoidPortIds,
                        null
                ))
        );
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        } finally {
            inFlightCalculations.remove(key, future);
        }
    }

    private String buildCalculationKey(String startPortId,
                                       String endPortId,
                                       List<String> requestedViaPortIds,
                                       boolean enforceMandatoryViaPorts,
                                       Set<String> avoidPortIds) {
        String viaKey = requestedViaPortIds == null ? "" : String.join(",", requestedViaPortIds);
        String avoidKey = avoidPortIds.stream().sorted().collect(Collectors.joining(","));
        return startPortId + "|" + endPortId + "|" + viaKey + "|" + enforceMandatoryViaPorts + "|" + avoidKey;
    }

    private RouteCalculationResource calculateOptimalRouteInternal(String startPortId,
                                                                   String endPortId,
                                                                   List<String> requestedViaPortIds,
                                                                   boolean enforceMandatoryViaPorts,
                                                                   Set<String> avoidPortIds,
                                                                   RouteHistoryContext historyContext) {
        Port startPort = findPortByIdOrThrow(startPortId);
        Port endPort = findPortByIdOrThrow(endPortId);

        RouteComputationResult result;
        if (requestedViaPortIds == null || requestedViaPortIds.isEmpty()) {
            result = computeRoute(startPort, endPort, avoidPortIds, false, List.of(), List.of(), List.of());
        } else if (enforceMandatoryViaPorts) {
            result = computeRouteThroughMandatoryPorts(startPort, endPort, requestedViaPortIds, avoidPortIds, historyContext);
        } else {
            result = computeRoute(
                    startPort,
                    endPort,
                    avoidPortIds,
                    false,
                    requestedViaPortIds,
                    List.of(),
                    List.of("Se ignoraron puertos intermedios porque no se solicitaron como obligatorios.")
            );
        }

        recordRouteSearch(startPort, endPort);
        persistSuccessfulHistory(historyContext, startPort, endPort, result, historyContext != null ? historyContext.routeId() : null);
        return result.response();
    }

    public RouteRecalculationResource recalculateRouteAvoidingDisabledPorts(String routeId) {
        return recalculateRouteAvoidingDisabledPorts(routeId, null);
    }

    public RouteRecalculationResource recalculateRouteAvoidingDisabledPorts(String routeId, RouteHistoryContext historyContext) {
        RouteDocument routeDocument = routeRepository.findById(routeId)
                .orElseThrow(() -> new RouteNotFoundException("Route not found: " + routeId));

        Port startPort = findPortByNameAndContinentOrThrow(routeDocument.getHomePort(), routeDocument.getHomePortContinent());
        Port endPort = findPortByNameAndContinentOrThrow(routeDocument.getDestinationPort(), routeDocument.getDestinationPortContinent());

        RouteComputationResult current = computeRoute(startPort, endPort, Collections.emptySet(), true, List.of(), List.of(), List.of());

        List<String> disabledPortIds = current.path().principalPorts().stream()
                .filter(Port::isDisabled)
                .map(Port::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (disabledPortIds.isEmpty()) {
            persistSuccessfulHistory(historyContext, startPort, endPort, current, routeId);
            return new RouteRecalculationResource(
                    routeId,
                    current.response().optimalRoute(),
                    false,
                    List.of(),
                    current.response().metadata()
            );
        }

        Set<String> avoidPortIds = new HashSet<>(disabledPortIds);

        if (avoidPortIds.contains(startPort.getId()) || avoidPortIds.contains(endPort.getId())) {
            throw new NoViableRouteAvoidingDisabledPortsException(
                    "Cannot recalculate route %s because one endpoint is disabled.".formatted(routeId),
                    avoidPortIds
            );
        }

        logger.info("route.recalculate routeId={} avoidedPortCount={}", routeId, avoidPortIds.size());

        try {
            RouteComputationResult recalculated = computeRoute(startPort, endPort, avoidPortIds, false, List.of(), List.of(), List.of());
            persistSuccessfulHistory(historyContext, startPort, endPort, recalculated, routeId);
            return new RouteRecalculationResource(
                    routeId,
                    recalculated.response().optimalRoute(),
                    true,
                    new ArrayList<>(avoidPortIds),
                    recalculated.response().metadata()
            );
        } catch (RouteNotFoundException ex) {
            persistNoViableHistory(historyContext, startPort, endPort, avoidPortIds, routeId, ex.getMessage());
            throw new NoViableRouteAvoidingDisabledPortsException(
                    "No viable route for %s when avoiding %d disabled ports.".formatted(routeId, avoidPortIds.size()),
                    avoidPortIds
            );
        }
    }

    public double calculateTotalDistance(List<Port> route) {
        double total = 0.0;
        for (int i = 0; i < route.size() - 1; i++) {
            Port currentPort = route.get(i);
            Port nextPort = route.get(i + 1);

            double segmentDistance = routeRepository.findByHomePortAndDestinationPort(currentPort.getName(), nextPort.getName())
                    .map(RouteDocument::getDistance)
                    .orElseGet(() -> {
                        logger.warn("Ruta no documentada entre {} y {}. Usando distancia Haversine como fallback.",
                                currentPort.getName(), nextPort.getName());
                        return geoUtils.calculateHaversineDistance(currentPort, nextPort);
                    });

            total += segmentDistance;
        }
        return total;
    }

    private void persistSuccessfulHistory(RouteHistoryContext context,
                                          Port startPort,
                                          Port endPort,
                                          RouteComputationResult computationResult,
                                          String routeId) {
        if (!shouldPersistHistory(context)) {
            return;
        }
        RouteCalculationResource response = computationResult.response();
        List<String> waypointIds = extractWaypointPortIds(computationResult.path());
        List<String> avoidedIds = toSortedList(computationResult.effectiveAvoidPortIds());
        RouteHistoryPersistRequest request = RouteHistoryPersistRequest.builder()
                .userId(context.userId())
                .tenantId(context.tenantId())
                .routeId(routeId != null ? routeId : context.routeId())
                .originPortId(startPort.getId())
                .originPortName(startPort.getName())
                .destinationPortId(endPort.getId())
                .destinationPortName(endPort.getName())
                .waypointPortIds(waypointIds)
                .avoidedPortIds(avoidedIds)
                .totalDistance(response.totalDistance())
                .durationEstimate(context.durationEstimate())
                .costEstimate(context.costEstimate())
                .status(RouteHistoryStatus.SUCCESS)
                .source(context.source() != null ? context.source() : RouteHistorySource.MANUAL)
                .notes(context.notes())
                .engineVersion(context.engineVersion())
                .pathEncoding(context.pathEncoding())
                .geojson(context.geojson())
                .metadata(buildMetadata(context, computationResult.path().principalPorts().size(), avoidedIds.size()))
                .build();
        routeHistoryService.save(request);
    }

    private void persistNoViableHistory(RouteHistoryContext context,
                                        Port startPort,
                                        Port endPort,
                                        Set<String> avoidPortIds,
                                        String routeId,
                                        String notes) {
        if (!shouldPersistHistory(context)) {
            return;
        }
        List<String> avoidedIds = toSortedList(avoidPortIds);
        RouteHistoryPersistRequest request = RouteHistoryPersistRequest.builder()
                .userId(context.userId())
                .tenantId(context.tenantId())
                .routeId(routeId != null ? routeId : context.routeId())
                .originPortId(startPort != null ? startPort.getId() : null)
                .originPortName(startPort != null ? startPort.getName() : null)
                .destinationPortId(endPort != null ? endPort.getId() : null)
                .destinationPortName(endPort != null ? endPort.getName() : null)
                .waypointPortIds(List.of())
                .avoidedPortIds(avoidedIds)
                .status(RouteHistoryStatus.NO_VIABLE_ROUTE)
                .source(context.source() != null ? context.source() : RouteHistorySource.MANUAL)
                .notes(notes != null ? notes : context.notes())
                .engineVersion(context.engineVersion())
                .pathEncoding(context.pathEncoding())
                .geojson(context.geojson())
                .metadata(buildMetadata(context, 0, avoidedIds.size()))
                .build();
        routeHistoryService.save(request);
    }

    private boolean shouldPersistHistory(RouteHistoryContext context) {
        return context != null && context.userId() != null;
    }

    private List<String> extractWaypointPortIds(RoutePath path) {
        List<Port> ports = path.principalPorts();
        if (ports.size() <= 2) {
            return List.of();
        }
        return ports.subList(1, ports.size() - 1).stream()
                .map(Port::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> toSortedList(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().sorted().toList();
    }

    private Map<String, Object> buildMetadata(RouteHistoryContext context, int portCount, int avoidedCount) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portCount", portCount);
        metadata.put("avoidedPortCount", avoidedCount);
        if (context != null && context.metadata() != null) {
            metadata.putAll(context.metadata());
        }
        return metadata;
    }

    private Port findPortByIdOrThrow(String portId) {
        return portRepository.findById(portId)
                .map(portMapper::toDomain)
                .orElseThrow(() -> new PortNotFoundException("Puerto no encontrado: " + portId));
    }

    private Port findPortByNameAndContinentOrThrow(String name, String continent) {
        return portRepository.findByNameAndContinent(name, continent)
                .map(portMapper::toDomain)
                .orElseThrow(() -> new PortNotFoundException(
                        "Puerto no encontrado: %s (%s)".formatted(name, continent)));
    }

    private Map<String, CoordinatesResource> createCoordinatesMapping(List<Port> ports) {
        return ports.stream()
                .collect(Collectors.toMap(
                        Port::getName,
                        portMapper::toResource,
                        (existing, replacement) -> existing
                ));
    }

    private RouteMetadataResource createRouteMetadata(RoutePath path,
                                                      List<String> requestedViaPortIds,
                                                      List<String> appliedViaPortIds) {
        if (path == null || path.principalPorts().isEmpty()) {
            return RouteMetadataResource.empty();
        }

        List<String> portIds = path.principalPorts().stream()
                .map(Port::getId)
                .filter(Objects::nonNull)
                .toList();

        List<MaritimeWaypointResource> waypoints = path.orderedWaypoints().stream()
                .filter(node -> node.getCoordinates() != null)
                .map(node -> new MaritimeWaypointResource(
                        node.getId(),
                        node.getName(),
                        node.getCoordinates().latitude(),
                        node.getCoordinates().longitude(),
                        node.getType().name()
                ))
                .toList();

        List<List<Double>> geometry = path.geometry().stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList();

        return new RouteMetadataResource(
                portIds,
                List.copyOf(requestedViaPortIds),
                List.copyOf(appliedViaPortIds),
                waypoints,
                geometry,
                path.estimatedHours(),
                "A_STAR",
                "AIS_OVERLAY"
        );
    }

    private RouteComputationResult computeRoute(Port startPort,
                                                Port endPort,
                                                Set<String> avoidPortIds,
                                                boolean includeDisabledPorts,
                                                List<String> requestedViaPortIds,
                                                List<String> appliedViaPortIds,
                                                List<String> extraWarnings) {
        Set<String> disabledPortIds = loadDisabledPortIds();
        validateEndpointsAvailability(startPort, endPort, disabledPortIds);

        Set<String> effectiveAvoidPortIds = new HashSet<>(avoidPortIds);
        if (!includeDisabledPorts) {
            effectiveAvoidPortIds.addAll(disabledPortIds);
        }

        RoutePath routePath = routeCalculatorService.calculateOptimalRoute(startPort, endPort, effectiveAvoidPortIds);
        List<String> warnings = new ArrayList<>(routePath.warnings());
        warnings.addAll(safetyValidator.validateFullRoute(routePath.principalPorts()));
        warnings.addAll(extraWarnings);
        RouteCalculationResource response = new RouteCalculationResource(
                routePath.principalPorts().stream().map(Port::getName).toList(),
                routePath.totalDistanceNm(),
                List.copyOf(warnings),
                createCoordinatesMapping(routePath.principalPorts()),
                createRouteMetadata(routePath, requestedViaPortIds, appliedViaPortIds)
        );
        return new RouteComputationResult(routePath, response, Collections.unmodifiableSet(effectiveAvoidPortIds));
    }

    private RouteComputationResult computeRouteThroughMandatoryPorts(Port startPort,
                                                                     Port endPort,
                                                                     List<String> requestedViaPortIds,
                                                                     Set<String> avoidPortIds,
                                                                     RouteHistoryContext historyContext) {
        List<Port> viaPorts = requestedViaPortIds.stream()
                .map(this::findPortByIdOrThrow)
                .toList();

        if (viaPorts.stream().map(Port::getId).anyMatch(avoidPortIds::contains)) {
            throw new RouteNotFoundException("No se puede calcular la ruta: un puerto intermedio obligatorio esta bloqueado.");
        }

        List<Port> sequence = new ArrayList<>();
        sequence.add(startPort);
        sequence.addAll(viaPorts);
        sequence.add(endPort);

        List<RoutePath> segments = new ArrayList<>();
        Set<String> effectiveAvoidedIds = new HashSet<>(avoidPortIds);
        for (int index = 0; index < sequence.size() - 1; index++) {
            Port from = sequence.get(index);
            Port to = sequence.get(index + 1);
            segments.add(routeCalculatorService.calculateOptimalRoute(from, to, effectiveAvoidedIds));
        }

        RoutePath mergedPath = mergeSegments(segments);
        List<String> appliedViaPortIds = viaPorts.stream()
                .map(Port::getId)
                .filter(Objects::nonNull)
                .toList();

        List<String> warnings = List.of("La ruta incluye puertos intermedios obligatorios.");
        RouteCalculationResource response = new RouteCalculationResource(
                mergedPath.principalPorts().stream().map(Port::getName).toList(),
                mergedPath.totalDistanceNm(),
                mergeWarnings(mergedPath, warnings),
                createCoordinatesMapping(mergedPath.principalPorts()),
                createRouteMetadata(mergedPath, requestedViaPortIds, appliedViaPortIds)
        );
        return new RouteComputationResult(mergedPath, response, Collections.unmodifiableSet(effectiveAvoidedIds));
    }

    private RoutePath mergeSegments(List<RoutePath> segments) {
        List<org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode> orderedNodes = new ArrayList<>();
        List<Port> principalPorts = new ArrayList<>();
        List<org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode> orderedWaypoints = new ArrayList<>();
        List<org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates> geometry = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double totalDistanceNm = 0.0;
        double estimatedHours = 0.0;

        for (int index = 0; index < segments.size(); index++) {
            RoutePath segment = segments.get(index);
            appendDistinct(orderedNodes, segment.orderedNodes(), index > 0);
            appendDistinctPorts(principalPorts, segment.principalPorts());
            orderedWaypoints.addAll(segment.orderedWaypoints());
            appendDistinctCoordinates(geometry, segment.geometry());
            warnings.addAll(segment.warnings());
            totalDistanceNm += segment.totalDistanceNm();
            estimatedHours += segment.estimatedHours();
        }

        return new RoutePath(
                List.copyOf(orderedNodes),
                List.copyOf(principalPorts),
                List.copyOf(orderedWaypoints),
                List.copyOf(geometry),
                totalDistanceNm,
                estimatedHours,
                List.copyOf(warnings)
        );
    }

    private List<String> mergeWarnings(RoutePath path, List<String> extraWarnings) {
        ArrayList<String> warnings = new ArrayList<>(path.warnings());
        warnings.addAll(safetyValidator.validateFullRoute(path.principalPorts()));
        warnings.addAll(extraWarnings);
        return List.copyOf(warnings);
    }

    private <T> void appendDistinct(List<T> target, List<T> values, boolean skipFirstValue) {
        for (int index = 0; index < values.size(); index++) {
            if (skipFirstValue && index == 0) {
                continue;
            }
            T value = values.get(index);
            if (target.isEmpty() || !target.get(target.size() - 1).equals(value)) {
                target.add(value);
            }
        }
    }

    private void appendDistinctPorts(List<Port> target, List<Port> values) {
        LinkedHashMap<String, Port> portsById = new LinkedHashMap<>();
        for (Port port : target) {
            portsById.put(port.getId() != null ? port.getId() : port.getName() + ":" + port.getContinent(), port);
        }
        for (Port port : values) {
            portsById.putIfAbsent(port.getId() != null ? port.getId() : port.getName() + ":" + port.getContinent(), port);
        }
        target.clear();
        target.addAll(portsById.values());
    }

    private void appendDistinctCoordinates(List<org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates> target,
                                           List<org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates> values) {
        for (org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates value : values) {
            if (target.isEmpty() || !target.get(target.size() - 1).equals(value)) {
                target.add(value);
            }
        }
    }

    private Set<String> loadDisabledPortIds() {
        return portRepository.findByDisabled(true).stream()
                .map(PortDocument::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void validateEndpointsAvailability(Port startPort, Port endPort, Set<String> disabledPortIds) {
        if (startPort.getId() != null && disabledPortIds.contains(startPort.getId())) {
            throw new RouteNotFoundException("El puerto de origen '%s' esta deshabilitado temporalmente.".formatted(startPort.getName()));
        }
        if (endPort.getId() != null && disabledPortIds.contains(endPort.getId())) {
            throw new RouteNotFoundException("El puerto de destino '%s' esta deshabilitado temporalmente.".formatted(endPort.getName()));
        }
    }

    private void recordRouteSearch(Port startPort, Port endPort) {
        try {
            String routeId = routeRepository.findByHomePortAndDestinationPort(startPort.getName(), endPort.getName())
                    .map(RouteDocument::getId)
                    .orElse(null);
            routePopularityService.registerSearch(startPort, endPort, routeId);
        } catch (Exception ex) {
            logger.warn("route.popularity.failed origin={} destination={} message={}",
                    startPort != null ? startPort.getName() : "unknown",
                    endPort != null ? endPort.getName() : "unknown",
                    ex.getMessage());
        }
    }
    private record RouteComputationResult(RoutePath path, RouteCalculationResource response, Set<String> effectiveAvoidPortIds) {}
}
