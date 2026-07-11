package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchClient;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchProperties;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.PortDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.GlobalFishingWatchOverlayDocument;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.GlobalFishingWatchOverlayRepository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories.PortRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GlobalFishingWatchCorridorOverlayProvider implements MaritimeCorridorOverlayProvider {

    private static final Logger logger = LoggerFactory.getLogger(GlobalFishingWatchCorridorOverlayProvider.class);
    private static final int MAX_CELLS_PER_BUCKET = 3;
    private static final double MIN_BUCKET_DEGREES = 1.5;
    private static final double MAX_BUCKET_DEGREES = 7.0;
    private static final double CROSS_REGION_DISTANCE_FACTOR = 1.35;
    private static final double SAME_REGION_SUPPORT_SPACING_NM = 140.0;
    private static final double SAME_REGION_SUPPORT_RADIUS_NM = 115.0;
    private static final double CROSS_REGION_SUPPORT_SPACING_NM = 170.0;
    private static final double CROSS_REGION_SUPPORT_RADIUS_NM = 140.0;
    private static final int PRIMARY_BUCKET_NEIGHBOR_RANGE = 1;
    private static final int SECONDARY_BUCKET_NEIGHBOR_RANGE = 2;

    private final GlobalFishingWatchClient client;
    private final GlobalFishingWatchProperties properties;
    private final GeoUtils geoUtils;
    private final MaritimeLandMask landMask;
    private final GlobalFishingWatchOverlayRepository overlayRepository;
    private final PortRepository portRepository;
    private final AtomicReference<MaritimeCorridorOverlay> cache = new AtomicReference<>();
    private final Object refreshMonitor = new Object();
    private volatile String cacheConfigHash;
    private CompletableFuture<MaritimeCorridorOverlay> inFlightRefresh;

    @Autowired
    public GlobalFishingWatchCorridorOverlayProvider(GlobalFishingWatchClient client,
                                                     GlobalFishingWatchProperties properties,
                                                     MaritimeNetworkCatalog ignoredNetworkCatalog,
                                                     GeoUtils geoUtils,
                                                     MaritimeLandMask landMask,
                                                     GlobalFishingWatchOverlayRepository overlayRepository,
                                                     PortRepository portRepository) {
        this.client = client;
        this.properties = properties;
        this.geoUtils = geoUtils;
        this.landMask = landMask;
        this.overlayRepository = overlayRepository;
        this.portRepository = portRepository;
        this.cache.set(MaritimeCorridorOverlay.empty("GLOBAL_FISHING_WATCH"));
        loadPersistedOverlay().ifPresent(cache::set);
    }

    public GlobalFishingWatchCorridorOverlayProvider(GlobalFishingWatchClient client,
                                                     GlobalFishingWatchProperties properties,
                                                     MaritimeNetworkCatalog ignoredNetworkCatalog,
                                                     GeoUtils geoUtils,
                                                     MaritimeLandMask landMask) {
        this.client = client;
        this.properties = properties;
        this.geoUtils = geoUtils;
        this.landMask = landMask;
        this.overlayRepository = null;
        this.portRepository = null;
        this.cache.set(MaritimeCorridorOverlay.empty("GLOBAL_FISHING_WATCH"));
    }

    @Override
    public MaritimeCorridorOverlay currentOverlay() {
        String currentConfigHash = computeConfigHash(activeRegions());
        MaritimeCorridorOverlay current = cache.get();
        if (!current.isEmpty() && currentConfigHash.equals(cacheConfigHash)) {
            return current;
        }
        return loadPersistedOverlay()
                .map(overlay -> {
                    cache.set(overlay);
                    return overlay;
                })
                .orElse(current);
    }

    @Override
    public MaritimeCorridorOverlay refreshOverlay() {
        if (!properties.isConfigured()) {
            MaritimeCorridorOverlay disabled = new MaritimeCorridorOverlay(
                    "GLOBAL_FISHING_WATCH",
                    Instant.now(),
                    List.of(),
                    List.of(),
                    List.of("Global Fishing Watch esta deshabilitado o no tiene token configurado.")
            );
            cache.set(disabled);
            return disabled;
        }

        return waitForRefresh(scheduleRefreshIfNeeded());
    }

    private CompletableFuture<MaritimeCorridorOverlay> scheduleRefreshIfNeeded() {
        synchronized (refreshMonitor) {
            if (inFlightRefresh != null) {
                return inFlightRefresh;
            }

            CompletableFuture<MaritimeCorridorOverlay> future = CompletableFuture.supplyAsync(this::buildOverlay);
            inFlightRefresh = future;
            future.whenComplete((overlay, throwable) -> {
                try {
                    if (throwable == null && overlay != null) {
                        cache.set(overlay);
                        persistOverlay(overlay);
                    }
                } finally {
                    synchronized (refreshMonitor) {
                        if (inFlightRefresh == future) {
                            inFlightRefresh = null;
                        }
                    }
                }
            });
            return future;
        }
    }

    private java.util.Optional<MaritimeCorridorOverlay> loadPersistedOverlay() {
        if (overlayRepository == null) {
            return java.util.Optional.empty();
        }

        String configHash = computeConfigHash(activeRegions());
        try {
            return overlayRepository
                    .findFirstBySourceAndConfigHashOrderByRefreshedAtDesc("GLOBAL_FISHING_WATCH", configHash)
                    .map(GlobalFishingWatchOverlayDocument::toOverlay)
                    .filter(overlay -> !overlay.isEmpty())
                    .map(overlay -> {
                        cacheConfigHash = configHash;
                        logger.info("gfw.overlay.loaded source={} nodes={} edges={} refreshedAt={} configHash={}",
                                overlay.source(),
                                overlay.nodeCount(),
                                overlay.edgeCount(),
                                overlay.refreshedAt(),
                                configHash);
                        return overlay;
                    });
        } catch (Exception ex) {
            logger.warn("gfw.overlay.load.failed configHash={} message={}", configHash, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    private void persistOverlay(MaritimeCorridorOverlay overlay) {
        if (overlayRepository == null || overlay == null || overlay.isEmpty()) {
            return;
        }

        String configHash = computeConfigHash(activeRegions());
        try {
            overlayRepository.save(GlobalFishingWatchOverlayDocument.fromOverlay(configHash, overlay));
            cacheConfigHash = configHash;
            logger.info("gfw.overlay.saved source={} nodes={} edges={} refreshedAt={} configHash={}",
                    overlay.source(),
                    overlay.nodeCount(),
                    overlay.edgeCount(),
                    overlay.refreshedAt(),
                    configHash);
        } catch (Exception ex) {
            logger.warn("gfw.overlay.save.failed configHash={} message={}", configHash, ex.getMessage());
        }
    }

    private MaritimeCorridorOverlay waitForRefresh(CompletableFuture<MaritimeCorridorOverlay> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private MaritimeCorridorOverlay buildOverlay() {
        Map<String, MaritimeNode> nodesById = new LinkedHashMap<>();
        Map<String, List<MaritimeNode>> nodesByRegion = new LinkedHashMap<>();
        LinkedHashSet<String> encodedEdges = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        List<GlobalFishingWatchProperties.RegionProperties> regions = activeRegions();
        logger.info("gfw.overlay.refresh.regions configuredRegions={} portApproachRegions={} totalRegions={}",
                properties.getRegions().size(),
                Math.max(0, regions.size() - properties.getRegions().size()),
                regions.size());

        for (GlobalFishingWatchProperties.RegionProperties region : regions) {
            List<GlobalFishingWatchClient.PresenceCell> rawCells = client.fetchPresenceCells(region);
            List<GlobalFishingWatchClient.PresenceCell> cells = selectDistributedCells(region, rawCells);

            logger.info("gfw.overlay.region region={} rawCells={} keptCells={} minHours={} minVesselIds={}",
                    region.getId(),
                    rawCells.size(),
                    cells.size(),
                    properties.getMinHours(),
                    properties.getMinVesselIds());

            if (cells.isEmpty()) {
                warnings.add("Sin celdas AIS para la region %s".formatted(region.getId()));
                continue;
            }

            double latStep = computeBucketStep(region.getMaxLat() - region.getMinLat());
            double lonStep = computeBucketStep(region.getMaxLon() - region.getMinLon());
            List<MaritimeNode> regionNodes = cells.stream()
                    .map(cell -> toNode(region, cell))
                    .filter(node -> !landMask.isOnLand(node.getCoordinates()))
                    .map(node -> nodesById.computeIfAbsent(node.getId(), ignored -> node))
                    .toList();
            nodesByRegion.put(region.getId(), regionNodes);

            connectRegionNeighbors(region, regionNodes, latStep, lonStep, encodedEdges);
        }

        connectCrossRegionNeighbors(nodesByRegion, encodedEdges);

        List<MaritimeNetworkCatalog.EdgeDefinition> overlayEdges = encodedEdges.stream()
                .filter(encoded -> isNavigableOverlayEdge(encoded, nodesById))
                .map(this::decodeEdge)
                .toList();

        logger.info("gfw.overlay.built nodes={} edges={} warnings={}", nodesById.size(), overlayEdges.size(), warnings.size());
        cacheConfigHash = computeConfigHash(regions);
        return new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                Instant.now(),
                List.copyOf(nodesById.values()),
                overlayEdges,
                List.copyOf(warnings)
        );
    }

    private List<GlobalFishingWatchProperties.RegionProperties> activeRegions() {
        LinkedHashMap<String, GlobalFishingWatchProperties.RegionProperties> regionsById = new LinkedHashMap<>();
        for (GlobalFishingWatchProperties.RegionProperties region : properties.getRegions()) {
            regionsById.put(region.getId(), region);
        }
        for (GlobalFishingWatchProperties.RegionProperties region : portApproachRegions()) {
            regionsById.putIfAbsent(region.getId(), region);
        }
        return List.copyOf(regionsById.values());
    }

    private List<GlobalFishingWatchProperties.RegionProperties> portApproachRegions() {
        if (!properties.isIncludePortApproachRegions()
                || portRepository == null
                || properties.getMaxPortApproachRegions() == 0) {
            return List.of();
        }

        Map<String, RegionBounds> boundsByGrid = new LinkedHashMap<>();
        double gridDegrees = properties.getPortApproachRegionGridDegrees();
        double radiusDegrees = properties.getPortApproachRegionRadiusDegrees();
        try {
            for (PortDocument port : portRepository.findAll()) {
                if (port == null || port.isDisabled() || port.getCoordinates() == null) {
                    continue;
                }
                double latitude = clampLatitude(port.getCoordinates().getLatitude());
                double longitude = normalizeLongitude(port.getCoordinates().getLongitude());
                int latBucket = (int) Math.floor((latitude + 90.0) / gridDegrees);
                int lonBucket = (int) Math.floor((longitude + 180.0) / gridDegrees);
                String key = latBucket + ":" + lonBucket;
                boundsByGrid
                        .computeIfAbsent(key, ignored -> new RegionBounds())
                        .include(latitude, longitude, radiusDegrees);
            }
        } catch (Exception ex) {
            logger.warn("gfw.overlay.port-approach-regions.failed message={}", ex.getMessage());
            return List.of();
        }

        List<GlobalFishingWatchProperties.RegionProperties> generated = boundsByGrid.entrySet().stream()
                .limit(properties.getMaxPortApproachRegions())
                .map(entry -> entry.getValue().toRegion("port-approach-" + entry.getKey().replace(':', '-')))
                .toList();
        if (boundsByGrid.size() > generated.size()) {
            logger.warn("gfw.overlay.port-approach-regions.truncated generated={} max={}",
                    boundsByGrid.size(),
                    properties.getMaxPortApproachRegions());
        }
        return generated;
    }

    private boolean isUsefulCell(GlobalFishingWatchClient.PresenceCell cell) {
        return cell.hours() >= properties.getMinHours()
                && cell.vesselIds() >= properties.getMinVesselIds();
    }

    private List<GlobalFishingWatchClient.PresenceCell> selectDistributedCells(
            GlobalFishingWatchProperties.RegionProperties region,
            List<GlobalFishingWatchClient.PresenceCell> rawCells
    ) {
        List<GlobalFishingWatchClient.PresenceCell> usefulCells = rawCells.stream()
                .filter(this::isUsefulCell)
                .sorted(Comparator.comparingDouble(this::cellPriority).reversed())
                .toList();

        if (usefulCells.size() <= properties.getMaxCellsPerRegion()) {
            return usefulCells;
        }

        double latStep = computeBucketStep(region.getMaxLat() - region.getMinLat());
        double lonStep = computeBucketStep(region.getMaxLon() - region.getMinLon());
        Map<String, List<GlobalFishingWatchClient.PresenceCell>> buckets = new LinkedHashMap<>();

        for (GlobalFishingWatchClient.PresenceCell cell : usefulCells) {
            String bucketKey = bucketKey(region, cell, latStep, lonStep);
            List<GlobalFishingWatchClient.PresenceCell> bucketCells = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayList<>());
            if (bucketCells.size() < MAX_CELLS_PER_BUCKET) {
                bucketCells.add(cell);
            }
        }

        List<GlobalFishingWatchClient.PresenceCell> distributed = new ArrayList<>();
        for (int index = 0; index < MAX_CELLS_PER_BUCKET && distributed.size() < properties.getMaxCellsPerRegion(); index++) {
            for (List<GlobalFishingWatchClient.PresenceCell> bucketCells : buckets.values()) {
                if (index < bucketCells.size()) {
                    distributed.add(bucketCells.get(index));
                    if (distributed.size() >= properties.getMaxCellsPerRegion()) {
                        break;
                    }
                }
            }
        }
        return distributed;
    }

    private double cellPriority(GlobalFishingWatchClient.PresenceCell cell) {
        return (cell.hours() * 10.0) + cell.vesselIds();
    }

    private double computeBucketStep(double spanDegrees) {
        int axisBuckets = Math.max(5, (int) Math.ceil(Math.sqrt(properties.getMaxCellsPerRegion() * 1.5)));
        double suggested = spanDegrees / axisBuckets;
        return Math.max(MIN_BUCKET_DEGREES, Math.min(MAX_BUCKET_DEGREES, suggested));
    }

    private String bucketKey(GlobalFishingWatchProperties.RegionProperties region,
                             GlobalFishingWatchClient.PresenceCell cell,
                             double latStep,
                             double lonStep) {
        int latBucket = (int) Math.floor((cell.latitude() - region.getMinLat()) / latStep);
        int lonBucket = (int) Math.floor((cell.longitude() - region.getMinLon()) / lonStep);
        return latBucket + ":" + lonBucket;
    }

    private MaritimeNode toNode(GlobalFishingWatchProperties.RegionProperties region,
                                GlobalFishingWatchClient.PresenceCell cell) {
        String latPart = formatCoord(cell.latitude());
        String lonPart = formatCoord(cell.longitude());
        return MaritimeNode.seaNode(
                "GFW:%s:%s:%s".formatted(region.getId(), latPart, lonPart),
                "GFW %s %.2f, %.2f".formatted(region.getName(), cell.latitude(), cell.longitude()),
                MaritimeNodeType.SEA_WAYPOINT,
                new Coordinates(cell.latitude(), cell.longitude())
        );
    }

    private void connectRegionNeighbors(GlobalFishingWatchProperties.RegionProperties region,
                                        List<MaritimeNode> regionNodes,
                                        double latStep,
                                        double lonStep,
                                        LinkedHashSet<String> encodedEdges) {
        Map<String, BucketCoordinate> bucketByNodeId = new LinkedHashMap<>();
        for (MaritimeNode node : regionNodes) {
            bucketByNodeId.put(node.getId(), bucketFor(region, node.getCoordinates(), latStep, lonStep));
        }

        for (MaritimeNode node : regionNodes) {
            List<NodeDistance> primaryCandidates = localMeshCandidates(
                    node,
                    regionNodes,
                    bucketByNodeId,
                    PRIMARY_BUCKET_NEIGHBOR_RANGE
            );
            primaryCandidates.stream()
                    .limit(properties.getNearestNeighbors())
                    .forEach(distance -> encodedEdges.add(encodeEdge(node.getId(), distance.node().getId())));

            if (primaryCandidates.size() >= properties.getNearestNeighbors()) {
                continue;
            }

            localMeshCandidates(node, regionNodes, bucketByNodeId, SECONDARY_BUCKET_NEIGHBOR_RANGE).stream()
                    .filter(distance -> primaryCandidates.stream().noneMatch(primary -> primary.node().equals(distance.node())))
                    .limit(properties.getNearestNeighbors() - primaryCandidates.size())
                    .forEach(distance -> encodedEdges.add(encodeEdge(node.getId(), distance.node().getId())));
        }
    }

    private void connectCrossRegionNeighbors(Map<String, List<MaritimeNode>> nodesByRegion, LinkedHashSet<String> encodedEdges) {
        List<RegionNode> allNodes = nodesByRegion.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(node -> new RegionNode(entry.getKey(), node)))
                .toList();
        int bridgeConnections = properties.getBridgeConnections();
        int candidateLimit = Math.max(bridgeConnections * 6, bridgeConnections);

        for (RegionNode current : allNodes) {
            Coordinates currentCoordinates = current.node().getCoordinates();
            allNodes.stream()
                    .filter(candidate -> !candidate.regionId().equals(current.regionId()))
                    .filter(candidate -> areCrossRegionNeighborsCompatible(current.regionId(), candidate.regionId()))
                    .filter(candidate -> !candidate.node().equals(current.node()))
                    .filter(candidate -> isWithinDistanceEnvelope(
                            currentCoordinates,
                            candidate.node().getCoordinates(),
                            maxCrossRegionDistanceNm(current.regionId(), candidate.regionId())
                    ))
                    .map(candidate -> new RegionNodeDistance(
                            candidate.regionId(),
                            candidate.node(),
                            geoUtils.calculateHaversineDistanceNm(currentCoordinates, candidate.node().getCoordinates())
                    ))
                    .filter(distance -> distance.distanceNm() <= maxCrossRegionDistanceNm(current.regionId(), distance.regionId()))
                    .sorted(Comparator.comparingDouble(RegionNodeDistance::distanceNm))
                    .limit(candidateLimit)
                    .filter(distance -> hasCorridorSupport(
                            current.node(),
                            distance.node(),
                            mergeSupportNodes(
                                    nodesByRegion.getOrDefault(current.regionId(), List.of()),
                                    nodesByRegion.getOrDefault(distance.regionId(), List.of())
                            ),
                            CROSS_REGION_SUPPORT_SPACING_NM,
                            CROSS_REGION_SUPPORT_RADIUS_NM
                    ))
                    .limit(bridgeConnections)
                    .forEach(distance -> encodedEdges.add(encodeEdge(current.node().getId(), distance.node().getId())));
        }
    }

    private boolean isWithinDistanceEnvelope(Coordinates first, Coordinates second, double maxDistanceNm) {
        double maxLatDelta = maxDistanceNm / 60.0;
        double latDelta = Math.abs(first.latitude() - second.latitude());
        if (latDelta > maxLatDelta) {
            return false;
        }

        double referenceLatitude = Math.toRadians((first.latitude() + second.latitude()) / 2.0);
        double lonNmPerDegree = Math.max(1.0, 60.0 * Math.cos(referenceLatitude));
        double maxLonDelta = maxDistanceNm / lonNmPerDegree;
        double lonDelta = Math.abs(first.longitude() - second.longitude());
        lonDelta = Math.min(lonDelta, 360.0 - lonDelta);
        return lonDelta <= maxLonDelta;
    }

    private boolean hasCorridorSupport(MaritimeNode from,
                                       MaritimeNode to,
                                       List<MaritimeNode> supportNodes,
                                       double sampleSpacingNm,
                                       double supportRadiusNm) {
        double distanceNm = geoUtils.calculateHaversineDistanceNm(from.getCoordinates(), to.getCoordinates());
        if (distanceNm <= sampleSpacingNm * 1.5) {
            return true;
        }

        List<Coordinates> sampledPath = geoUtils.densifyPath(List.of(from.getCoordinates(), to.getCoordinates()), sampleSpacingNm);
        if (sampledPath.size() <= 2) {
            return true;
        }

        for (int index = 1; index < sampledPath.size() - 1; index++) {
            Coordinates sample = sampledPath.get(index);
            boolean supported = supportNodes.stream()
                    .filter(node -> !node.equals(from) && !node.equals(to))
                    .anyMatch(node -> geoUtils.calculateHaversineDistanceNm(sample, node.getCoordinates()) <= supportRadiusNm);
            if (!supported) {
                return false;
            }
        }
        return true;
    }

    private List<NodeDistance> localMeshCandidates(MaritimeNode node,
                                                   List<MaritimeNode> regionNodes,
                                                   Map<String, BucketCoordinate> bucketByNodeId,
                                                   int bucketNeighborRange) {
        BucketCoordinate origin = bucketByNodeId.get(node.getId());
        return regionNodes.stream()
                .filter(candidate -> !candidate.equals(node))
                .filter(candidate -> bucketDistance(origin, bucketByNodeId.get(candidate.getId())) <= bucketNeighborRange)
                .map(candidate -> new NodeDistance(candidate, geoUtils.calculateHaversineDistanceNm(node.getCoordinates(), candidate.getCoordinates())))
                .filter(distance -> distance.distanceNm() <= maxNeighborDistanceNmForRegion(extractRegionId(node.getId())))
                .filter(distance -> hasCorridorSupport(
                        node,
                        distance.node(),
                        regionNodes,
                        SAME_REGION_SUPPORT_SPACING_NM,
                        SAME_REGION_SUPPORT_RADIUS_NM
                ))
                .sorted(Comparator
                        .comparingInt((NodeDistance distance) -> bucketDistance(origin, bucketByNodeId.get(distance.node().getId())))
                        .thenComparingDouble(NodeDistance::distanceNm))
                .toList();
    }

    private List<MaritimeNode> mergeSupportNodes(List<MaritimeNode> first, List<MaritimeNode> second) {
        LinkedHashMap<String, MaritimeNode> merged = new LinkedHashMap<>();
        first.forEach(node -> merged.put(node.getId(), node));
        second.forEach(node -> merged.put(node.getId(), node));
        return List.copyOf(merged.values());
    }

    private BucketCoordinate bucketFor(GlobalFishingWatchProperties.RegionProperties region,
                                       Coordinates coordinates,
                                       double latStep,
                                       double lonStep) {
        int latBucket = (int) Math.floor((coordinates.latitude() - region.getMinLat()) / latStep);
        int lonBucket = (int) Math.floor((coordinates.longitude() - region.getMinLon()) / lonStep);
        return new BucketCoordinate(latBucket, lonBucket);
    }

    private int bucketDistance(BucketCoordinate first, BucketCoordinate second) {
        return Math.max(
                Math.abs(first.latBucket() - second.latBucket()),
                Math.abs(first.lonBucket() - second.lonBucket())
        );
    }

    private String encodeEdge(String fromId, String toId) {
        return fromId.compareTo(toId) <= 0 ? fromId + "->" + toId : toId + "->" + fromId;
    }

    private MaritimeNetworkCatalog.EdgeDefinition decodeEdge(String encoded) {
        String[] parts = encoded.split("->", 2);
        return new MaritimeNetworkCatalog.EdgeDefinition(parts[0], parts[1], false, false, false);
    }

    private boolean isNavigableOverlayEdge(String encoded, Map<String, MaritimeNode> overlayNodesById) {
        MaritimeNetworkCatalog.EdgeDefinition edge = decodeEdge(encoded);
        MaritimeNode fromNode = overlayNodesById.get(edge.fromNodeId());
        MaritimeNode toNode = overlayNodesById.get(edge.toNodeId());
        if (fromNode == null || toNode == null) {
            return false;
        }

        return !landMask.crossesLand(fromNode.getCoordinates(), toNode.getCoordinates());
    }

    private double maxNeighborDistanceNmForRegion(String regionId) {
        if (regionId.startsWith("peru-ecuador-coast")) {
            return 260.0;
        }
        if (regionId.startsWith("caribbean-panama-west") || regionId.startsWith("caribbean-panama-east")) {
            return 220.0;
        }
        if (regionId.startsWith("iberia-atlantic-approach")) {
            return 200.0;
        }
        if (regionId.startsWith("iberia-mediterranean-approach")) {
            return 180.0;
        }
        return properties.getMaxNeighborDistanceNm();
    }

    private double maxCrossRegionDistanceNm(String firstRegionId, String secondRegionId) {
        if (firstRegionId.startsWith("peru-ecuador-coast") || secondRegionId.startsWith("peru-ecuador-coast")) {
            return 260.0;
        }
        if (firstRegionId.startsWith("caribbean-panama-west") || secondRegionId.startsWith("caribbean-panama-west")
                || firstRegionId.startsWith("caribbean-panama-east") || secondRegionId.startsWith("caribbean-panama-east")) {
            return 230.0;
        }
        if (firstRegionId.startsWith("iberia-atlantic-approach") || secondRegionId.startsWith("iberia-atlantic-approach")
                || firstRegionId.startsWith("iberia-mediterranean-approach") || secondRegionId.startsWith("iberia-mediterranean-approach")) {
            return 200.0;
        }
        return properties.getMaxNeighborDistanceNm() * CROSS_REGION_DISTANCE_FACTOR;
    }

    private boolean areCrossRegionNeighborsCompatible(String firstRegionId, String secondRegionId) {
        if (firstRegionId.startsWith("peru-ecuador-coast") || secondRegionId.startsWith("peru-ecuador-coast")) {
            return (firstRegionId.startsWith("peru-ecuador-coast") && secondRegionId.startsWith("peru-ecuador-coast"))
                    || firstRegionId.startsWith("north-pacific")
                    || firstRegionId.startsWith("south-pacific")
                    || secondRegionId.startsWith("north-pacific")
                    || secondRegionId.startsWith("south-pacific");
        }
        if (firstRegionId.startsWith("caribbean-panama-west") || firstRegionId.startsWith("caribbean-panama-east")
                || secondRegionId.startsWith("caribbean-panama-west") || secondRegionId.startsWith("caribbean-panama-east")) {
            return isAtlanticRegion(firstRegionId) && isAtlanticRegion(secondRegionId);
        }
        if (firstRegionId.startsWith("iberia-atlantic-approach") || secondRegionId.startsWith("iberia-atlantic-approach")) {
            return isAtlanticRegion(firstRegionId) && isAtlanticRegion(secondRegionId);
        }
        if (firstRegionId.startsWith("iberia-mediterranean-approach") || secondRegionId.startsWith("iberia-mediterranean-approach")) {
            return isMediterraneanRegion(firstRegionId) && isMediterraneanRegion(secondRegionId);
        }
        return true;
    }

    private boolean isAtlanticRegion(String regionId) {
        return regionId.startsWith("atlantic-")
                || regionId.startsWith("caribbean-panama-")
                || regionId.startsWith("iberia-atlantic-approach");
    }

    private boolean isMediterraneanRegion(String regionId) {
        return regionId.startsWith("mediterranean-")
                || regionId.startsWith("iberia-mediterranean-approach");
    }

    private String extractRegionId(String nodeId) {
        if (nodeId == null || !nodeId.startsWith("GFW:")) {
            return "";
        }
        int lastSeparator = nodeId.lastIndexOf(':');
        return lastSeparator > 4 ? nodeId.substring(4, lastSeparator) : "";
    }

    private String formatCoord(double value) {
        return Normalizer.normalize(String.format(java.util.Locale.US, "%.2f", value), Normalizer.Form.NFD)
                .replace('.', '_')
                .replace('-', 'm');
    }

    private double clampLatitude(double latitude) {
        return Math.max(-89.9999, Math.min(89.9999, latitude));
    }

    private double normalizeLongitude(double longitude) {
        double normalized = longitude;
        while (normalized < -180.0) {
            normalized += 360.0;
        }
        while (normalized > 180.0) {
            normalized -= 360.0;
        }
        return normalized;
    }

    private String computeConfigHash(List<GlobalFishingWatchProperties.RegionProperties> activeRegions) {
        String regions = activeRegions.stream()
                .map(region -> "%s:%s:%s:%s:%s".formatted(
                        region.getId(),
                        formatConfigDouble(region.getMinLat()),
                        formatConfigDouble(region.getMinLon()),
                        formatConfigDouble(region.getMaxLat()),
                        formatConfigDouble(region.getMaxLon())
                ))
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
        String vesselTypes = properties.getVesselTypes().stream()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
        String signature = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s".formatted(
                properties.getStartDate(),
                properties.getEndDate(),
                properties.getSpatialResolution(),
                properties.getTemporalResolution(),
                properties.getMaxCellsPerRegion(),
                properties.getNearestNeighbors(),
                properties.getBridgeConnections(),
                formatConfigDouble(properties.getMinHours()),
                properties.getMinVesselIds(),
                vesselTypes,
                formatConfigDouble(properties.getMaxNeighborDistanceNm()),
                formatConfigDouble(properties.getMaxBridgeDistanceNm()),
                regions
        );
        return sha256(signature);
    }

    private String formatConfigDouble(double value) {
        return String.format(java.util.Locale.US, "%.4f", value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no esta disponible", ex);
        }
    }

    private record NodeDistance(MaritimeNode node, double distanceNm) {
    }

    private record RegionNode(String regionId, MaritimeNode node) {
    }

    private record RegionNodeDistance(String regionId, MaritimeNode node, double distanceNm) {
    }

    private record BucketCoordinate(int latBucket, int lonBucket) {
    }

    private final class RegionBounds {
        private double minLat = Double.POSITIVE_INFINITY;
        private double minLon = Double.POSITIVE_INFINITY;
        private double maxLat = Double.NEGATIVE_INFINITY;
        private double maxLon = Double.NEGATIVE_INFINITY;

        private void include(double latitude, double longitude, double radiusDegrees) {
            minLat = Math.min(minLat, clampLatitude(latitude - radiusDegrees));
            maxLat = Math.max(maxLat, clampLatitude(latitude + radiusDegrees));
            minLon = Math.min(minLon, normalizeLongitude(longitude - radiusDegrees));
            maxLon = Math.max(maxLon, normalizeLongitude(longitude + radiusDegrees));
        }

        private GlobalFishingWatchProperties.RegionProperties toRegion(String id) {
            return new GlobalFishingWatchProperties.RegionProperties(
                    id,
                    "Port Approach AIS Region",
                    minLat,
                    minLon,
                    maxLat,
                    maxLon
            );
        }
    }
}
