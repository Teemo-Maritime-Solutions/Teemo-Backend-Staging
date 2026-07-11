package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeEdge;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RouteGraph;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.mappers.PortMapper;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.RouteRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class RouteGraphBuilder {
    private static final Logger logger = LoggerFactory.getLogger(RouteGraphBuilder.class);
    private static final int PORT_CONNECTIONS = 3;
    private static final int MAX_PORT_CANDIDATE_SCANS = 250;
    private static final double DEFAULT_SPEED_KNOTS = 18.0;
    private static final double MAX_DIRECT_PORT_OVERLAY_DISTANCE_NM = 650.0;
    private static final double MAX_PORT_CATALOG_CONNECTOR_DISTANCE_NM = 450.0;

    private final RouteRepository routeRepository;
    private final PortRepository portRepository;
    private final PortMapper portMapper;
    private final MaritimeNetworkCatalog networkCatalog;
    private final GeoUtils geoUtils;
    private final MaritimeLandMask landMask;
    private final MaritimeCorridorOverlayProvider overlayProvider;

    @Value("${routing.maritime.catalog-backbone.enabled:true}")
    private boolean catalogBackboneEnabled = true;

    private volatile GraphCache dynamicCache;

    @Autowired
    public RouteGraphBuilder(RouteRepository routeRepository,
                             PortRepository portRepository,
                             PortMapper portMapper,
                             MaritimeNetworkCatalog networkCatalog,
                             GeoUtils geoUtils,
                             MaritimeLandMask landMask,
                             MaritimeCorridorOverlayProvider overlayProvider) {
        this.routeRepository = routeRepository;
        this.portRepository = portRepository;
        this.portMapper = portMapper;
        this.networkCatalog = networkCatalog;
        this.geoUtils = geoUtils;
        this.landMask = landMask;
        this.overlayProvider = overlayProvider;
    }

    public RouteGraphBuilder(PortRepository portRepository,
                             PortMapper portMapper,
                             MaritimeNetworkCatalog networkCatalog,
                             GeoUtils geoUtils,
                             MaritimeLandMask landMask,
                             MaritimeCorridorOverlayProvider overlayProvider) {
        this(null, portRepository, portMapper, networkCatalog, geoUtils, landMask, overlayProvider);
    }

    public RouteGraph buildDynamicRouteGraph(Set<String> avoidPortIds) {
        return buildDynamicRouteGraph(avoidPortIds, Set.of(), true);
    }

    public RouteGraph buildDynamicRouteGraphForPorts(Set<String> avoidPortIds, Set<String> requiredPortIds) {
        return buildDynamicRouteGraph(avoidPortIds, requiredPortIds, false);
    }

    private RouteGraph buildDynamicRouteGraph(Set<String> avoidPortIds, Set<String> requiredPortIds, boolean connectAllPorts) {
        long startedAt = System.nanoTime();
        List<PortDocument> portDocuments = portRepository.findAll();
        boolean includeCatalogBackbone = catalogBackboneEnabled
                && !portDocuments.isEmpty()
                && (connectAllPorts || !requiredPortIds.isEmpty());
        MaritimeCorridorOverlay overlay = overlayProvider.currentOverlay();
        if (overlay.isEmpty() && !includeCatalogBackbone) {
            overlay = overlayProvider.refreshOverlay();
        }
        if (overlay.isEmpty() && includeCatalogBackbone) {
            logger.info("route.graph.dynamic.overlay.empty using catalog backbone without blocking AIS refresh");
        }
        if (!overlay.warnings().isEmpty()) {
            logger.warn("route.graph.dynamic.overlay.warning source={} warnings={}", overlay.source(), overlay.warnings());
        }
        String cacheKey = buildCacheKey(overlay, portDocuments, avoidPortIds, requiredPortIds, connectAllPorts);
        GraphCache cache = dynamicCache;
        if (cache != null && cache.key().equals(cacheKey)) {
            return cache.graph();
        }

        synchronized (this) {
            cache = dynamicCache;
            if (cache != null && cache.key().equals(cacheKey)) {
                return cache.graph();
            }
            return buildAndCacheDynamicRouteGraph(
                    startedAt,
                    overlay,
                    portDocuments,
                    avoidPortIds,
                    requiredPortIds,
                    connectAllPorts,
                    includeCatalogBackbone,
                    cacheKey
            );
        }
    }

    private RouteGraph buildAndCacheDynamicRouteGraph(long startedAt,
                                                      MaritimeCorridorOverlay overlay,
                                                      List<PortDocument> portDocuments,
                                                      Set<String> avoidPortIds,
                                                      Set<String> requiredPortIds,
                                                      boolean connectAllPorts,
                                                      boolean includeCatalogBackbone,
                                                      String cacheKey) {
        RouteGraph graph = new RouteGraph();
        Map<String, MaritimeNode> graphNodesById = new LinkedHashMap<>();
        Map<String, MaritimeNode> overlayNodesById = new LinkedHashMap<>();

        if (includeCatalogBackbone) {
            addCatalogBackbone(graph, graphNodesById);
        }

        for (MaritimeNode node : overlay.nodes()) {
            graph.addNode(node);
            graphNodesById.put(node.getId(), node);
            overlayNodesById.put(node.getId(), node);
        }

        for (MaritimeNetworkCatalog.EdgeDefinition edgeDefinition : overlay.edges()) {
            MaritimeNode fromNode = graphNodesById.get(edgeDefinition.fromNodeId());
            MaritimeNode toNode = graphNodesById.get(edgeDefinition.toNodeId());
            if (fromNode == null || toNode == null) {
                continue;
            }
            addEdge(graph, fromNode, toNode, edgeDefinition, false);
        }

        long overlayElapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        logger.info("route.graph.dynamic.overlay-added overlayNodes={} overlayEdges={} elapsedMs={}",
                overlay.nodeCount(),
                overlay.edgeCount(),
                overlayElapsedMs);
        connectPorts(graph, overlayNodesById, portDocuments, avoidPortIds, requiredPortIds, connectAllPorts, includeCatalogBackbone);
        dynamicCache = new GraphCache(cacheKey, graph);
        long elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        logger.info("route.graph.dynamic.built overlayNodes={} overlayEdges={} ports={} graphNodes={} graphEdges={} elapsedMs={}",
                overlay.nodeCount(),
                overlay.edgeCount(),
                portDocuments.size(),
                graph.getNodeCount(),
                graph.getEdgeCount(),
                elapsedMs);
        return graph;
    }

    public RouteGraph buildStaticRouteGraph(Set<String> avoidPortIds) {
        return buildDynamicRouteGraph(avoidPortIds);
    }

    public void prewarmBaseGraphs() {
        buildDynamicRouteGraph(Set.of(), Set.of(), false);
    }

    public void invalidateDynamicCache() {
        dynamicCache = null;
    }

    private void addCatalogBackbone(RouteGraph graph, Map<String, MaritimeNode> nodesById) {
        for (MaritimeNode node : networkCatalog.coreNodes()) {
            graph.addNode(node);
            nodesById.putIfAbsent(node.getId(), node);
        }

        for (MaritimeNetworkCatalog.EdgeDefinition edgeDefinition : networkCatalog.coreEdges()) {
            MaritimeNode fromNode = nodesById.get(edgeDefinition.fromNodeId());
            MaritimeNode toNode = nodesById.get(edgeDefinition.toNodeId());
            if (fromNode == null || toNode == null) {
                continue;
            }
            addEdge(graph, fromNode, toNode, edgeDefinition, true);
        }
    }

    private void connectPorts(RouteGraph graph,
                              Map<String, MaritimeNode> overlayNodes,
                              List<PortDocument> portDocuments,
                              Set<String> avoidPortIds,
                              Set<String> requiredPortIds,
                              boolean connectAllPorts,
                              boolean includeCatalogBackbone) {
        for (PortDocument portDocument : portDocuments) {
            Port port = portMapper.toDomain(portDocument);
            if (port.getId() != null && avoidPortIds.contains(port.getId())) {
                continue;
            }
            if (!connectAllPorts && (port.getId() == null || !requiredPortIds.contains(port.getId()))) {
                continue;
            }

            MaritimeNode portNode = graph.addPortNode(port);
            boolean connected = connectPortToOverlay(graph, portNode, port, overlayNodes);
            if (includeCatalogBackbone) {
                connected = connectPortViaCatalogConnector(graph, portNode, port) || connected;
            }
            if (!connected) {
                logger.warn("route.graph.port.unconnected portId={} portName={}", port.getId(), port.getName());
            }
        }
    }

    private boolean connectPortToOverlay(RouteGraph graph,
                                         MaritimeNode portNode,
                                         Port port,
                                         Map<String, MaritimeNode> overlayNodes) {
        List<MaritimeNode> candidates = nearestNavigableOverlayNodes(port, overlayNodes);
        for (MaritimeNode candidate : candidates) {
            graph.addBidirectionalEdge(connection(portNode, candidate));
        }
        return !candidates.isEmpty();
    }

    private boolean connectPortViaCatalogConnector(RouteGraph graph,
                                                   MaritimeNode portNode,
                                                   Port port) {
        for (String connectorId : networkCatalog.connectorIdsFor(port)) {
            MaritimeNode connector = networkCatalog.findNode(connectorId).orElse(null);
            if (connector == null) {
                continue;
            }

            double portConnectorDistanceNm = geoUtils.calculateHaversineDistanceNm(
                    port.getCoordinates(),
                    connector.getCoordinates()
            );
            if (!isNavigableCatalogPortAccess(port.getCoordinates(), connector.getCoordinates(), portConnectorDistanceNm)) {
                continue;
            }

            graph.addNode(connector);
            graph.addBidirectionalEdge(connection(portNode, connector));
            logger.info("route.graph.port.catalog-connector portId={} portName={} connectorId={}",
                    port.getId(),
                    port.getName(),
                    connectorId);
            return true;
        }
        return false;
    }

    private List<MaritimeNode> nearestNavigableOverlayNodes(Port port, Map<String, MaritimeNode> overlayNodes) {
        List<NodeDistance> sortedByDistance = overlayNodes.values().stream()
                .map(node -> new NodeDistance(
                        node,
                        geoUtils.calculateHaversineDistanceNm(port.getCoordinates(), node.getCoordinates())
                ))
                .sorted(Comparator.comparingDouble(NodeDistance::distanceNm))
                .toList();

        List<String> preferredRegionIds = networkCatalog.preferredOverlayRegionIdsFor(port);
        List<MaritimeNode> preferred = scanNavigableCandidates(port, sortedByDistance, preferredRegionIds);
        if (preferred.size() >= PORT_CONNECTIONS || preferredRegionIds.isEmpty()) {
            return preferred;
        }

        LinkedHashMap<String, MaritimeNode> merged = new LinkedHashMap<>();
        preferred.forEach(node -> merged.put(node.getId(), node));
        scanNavigableCandidates(port, sortedByDistance, List.of())
                .forEach(node -> merged.putIfAbsent(node.getId(), node));
        return merged.values().stream().limit(PORT_CONNECTIONS).toList();
    }

    private List<MaritimeNode> scanNavigableCandidates(Port port,
                                                       List<NodeDistance> sortedByDistance,
                                                       List<String> preferredRegionIds) {
        List<MaritimeNode> candidates = new java.util.ArrayList<>();
        int scanned = 0;
        for (NodeDistance distance : sortedByDistance) {
            MaritimeNode node = distance.node();
            if (!preferredRegionIds.isEmpty() && preferredRegionIds.stream().noneMatch(regionId -> node.getId().startsWith("GFW:" + regionId + ":"))) {
                continue;
            }
            scanned++;
            if (isNavigablePortAccess(port.getCoordinates(), node.getCoordinates(), distance.distanceNm())) {
                candidates.add(node);
                if (candidates.size() >= PORT_CONNECTIONS) {
                    break;
                }
            }
            if (scanned >= MAX_PORT_CANDIDATE_SCANS) {
                break;
            }
        }
        return candidates;
    }

    private void addEdge(RouteGraph graph,
                         MaritimeNode fromNode,
                         MaritimeNode toNode,
                         MaritimeNetworkCatalog.EdgeDefinition edgeDefinition,
                         boolean validateLandCrossing) {
        List<Coordinates> geometry = edgeDefinition.geometry().isEmpty()
                ? List.of(fromNode.getCoordinates(), toNode.getCoordinates())
                : edgeDefinition.geometry();
        if (validateLandCrossing && !edgeDefinition.canal() && landMask.crossesLand(geometry)) {
            return;
        }

        double distanceNm = pathDistanceNm(geometry);
        graph.addBidirectionalEdge(new MaritimeEdge(
                fromNode,
                toNode,
                distanceNm,
                distanceNm / DEFAULT_SPEED_KNOTS,
                edgeDefinition.restricted(),
                false,
                edgeDefinition.canal(),
                edgeDefinition.highRisk(),
                geometry
        ));
    }

    private MaritimeEdge connection(MaritimeNode portNode, MaritimeNode overlayNode) {
        List<Coordinates> geometry = List.of(portNode.getCoordinates(), overlayNode.getCoordinates());
        double distanceNm = pathDistanceNm(geometry);
        return new MaritimeEdge(
                portNode,
                overlayNode,
                distanceNm,
                distanceNm / DEFAULT_SPEED_KNOTS,
                false,
                false,
                false,
                false,
                geometry
        );
    }

    private boolean isNavigable(Coordinates from, Coordinates to) {
        return !landMask.crossesLand(List.of(from, to));
    }

    private boolean isNavigablePortAccess(Coordinates portCoordinates, Coordinates overlayCoordinates, double distanceNm) {
        if (distanceNm <= MAX_DIRECT_PORT_OVERLAY_DISTANCE_NM && isNavigable(portCoordinates, overlayCoordinates)) {
            return true;
        }

        // Some seeded ports use city-center coordinates instead of terminal or harbor coordinates.
        // For the short port-access leg, accept a nearby AIS cell when the AIS endpoint is offshore.
        return distanceNm <= 220.0 && !landMask.isOnLand(overlayCoordinates);
    }

    private boolean isNavigableCatalogPortAccess(Coordinates portCoordinates, Coordinates connectorCoordinates, double distanceNm) {
        if (isNavigable(portCoordinates, connectorCoordinates)) {
            return true;
        }

        return distanceNm <= MAX_PORT_CATALOG_CONNECTOR_DISTANCE_NM && !landMask.isOnLand(connectorCoordinates);
    }

    private double pathDistanceNm(List<Coordinates> geometry) {
        if (geometry == null || geometry.size() < 2) {
            return 0.0;
        }

        double total = 0.0;
        for (int index = 0; index < geometry.size() - 1; index++) {
            total += geoUtils.calculateHaversineDistanceNm(geometry.get(index), geometry.get(index + 1));
        }
        return total;
    }

    private String buildCacheKey(MaritimeCorridorOverlay overlay,
                                 List<PortDocument> portDocuments,
                                 Set<String> avoidPortIds,
                                 Set<String> requiredPortIds,
                                 boolean connectAllPorts) {
        String overlayStamp = overlay.refreshedAt() != null ? overlay.refreshedAt().toString() : "none";
        String overlaySizeStamp = overlay.nodeCount() + ":" + overlay.edgeCount();
        String portStamp = portDocuments.stream()
                .map(document -> document.getId() + ":" + document.getName() + ":" + document.isDisabled())
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
        String avoidStamp = avoidPortIds.stream().sorted().reduce((left, right) -> left + "|" + right).orElse("none");
        String requiredPortStamp = requiredPortIds.stream().sorted().reduce((left, right) -> left + "|" + right).orElse("none");
        String portScopeStamp = connectAllPorts ? "all" : requiredPortStamp;
        return overlayStamp + "::" + overlaySizeStamp + "::" + portStamp + "::" + avoidStamp + "::" + portScopeStamp;
    }

    private record GraphCache(String key, RouteGraph graph) {
    }

    private record NodeDistance(MaritimeNode node, double distanceNm) {
    }
}
