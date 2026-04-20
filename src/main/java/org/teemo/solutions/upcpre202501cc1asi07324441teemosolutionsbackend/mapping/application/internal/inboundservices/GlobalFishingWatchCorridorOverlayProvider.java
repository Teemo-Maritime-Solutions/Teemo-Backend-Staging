package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.Coordinates;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.GeoUtils;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeLandMask;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNode;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.MaritimeNodeType;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchClient;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw.GlobalFishingWatchProperties;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GlobalFishingWatchCorridorOverlayProvider implements MaritimeCorridorOverlayProvider {

    private static final Logger logger = LoggerFactory.getLogger(GlobalFishingWatchCorridorOverlayProvider.class);
    private static final Duration OVERLAY_REFRESH_TTL = Duration.ofHours(6);
    private static final int MAX_CELLS_PER_BUCKET = 3;
    private static final int CROSS_REGION_CONNECTIONS = 3;
    private static final double MIN_BUCKET_DEGREES = 1.5;
    private static final double MAX_BUCKET_DEGREES = 7.0;
    private static final double CROSS_REGION_DISTANCE_FACTOR = 1.35;
    private static final double SAME_REGION_SUPPORT_SPACING_NM = 140.0;
    private static final double SAME_REGION_SUPPORT_RADIUS_NM = 115.0;
    private static final double CROSS_REGION_SUPPORT_SPACING_NM = 170.0;
    private static final double CROSS_REGION_SUPPORT_RADIUS_NM = 140.0;
    private static final double BRIDGE_SUPPORT_RADIUS_NM = 125.0;
    private static final int PRIMARY_BUCKET_NEIGHBOR_RANGE = 1;
    private static final int SECONDARY_BUCKET_NEIGHBOR_RANGE = 2;
    private static final Set<String> PACIFIC_STATIC_NODES = Set.of(
            "CALIFORNIA_OFFSHORE", "BAJA_OFFSHORE", "PACIFIC_NORTHEAST", "PACIFIC_TROPICAL_EAST",
            "PACIFIC_SOUTH_EAST", "SOUTH_PACIFIC_EAST", "SOUTH_PACIFIC_CENTRAL", "SOUTH_PACIFIC_WEST",
            "CENTRAL_AMERICA_WEST", "ECUADOR_APPROACH", "ECUADOR_OUTER", "PERU_NORTHBOUND",
            "PERU_APPROACH", "CHILE_APPROACH", "PANAMA_PACIFIC_OUTER", "PANAMA_PACIFIC", "CAPE_HORN_WEST"
    );
    private static final Set<String> ATLANTIC_STATIC_NODES = Set.of(
            "PANAMA_ATLANTIC", "PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_SW", "CARIBBEAN_WEST",
            "CARIBBEAN_COLOMBIA", "CARIBBEAN", "CARIBBEAN_ARC", "CARIBBEAN_EAST", "LESSER_ANTILLES_OUTER",
            "FLORIDA_STRAITS", "US_EAST_COAST", "ATLANTIC_TRANSITION_WEST", "ATLANTIC_TRANSITION_CENTRAL",
            "NORTH_ATLANTIC_WEST", "NORTH_ATLANTIC_CENTRAL", "NORTH_ATLANTIC_EAST", "AZORES_CORRIDOR",
            "AZORES_SOUTH", "MADEIRA_APPROACH", "PORTUGAL_APPROACH", "IBERIA_WEST", "ATLANTIC_EQUATOR_WEST",
            "ATLANTIC_EQUATOR_EAST", "BRAZIL_NORTH", "BRAZIL_SOUTHEAST", "SOUTH_ATLANTIC_WEST",
            "RIO_PLATA_APPROACH", "CAPE_HORN_EAST", "WEST_AFRICA_NW", "WEST_AFRICA_CENTRAL",
            "WEST_AFRICA_SOUTH", "SOUTH_ATLANTIC_EAST", "SOUTH_AFRICA_WEST", "GIBRALTAR_WEST"
    );
    private static final Set<String> MEDITERRANEAN_STATIC_NODES = Set.of(
            "ALBORAN_SEA", "WEST_MEDITERRANEAN", "TYRRHENIAN_SEA", "CENTRAL_MEDITERRANEAN",
            "IONIAN_SEA", "AEGEAN_SEA", "EAST_MEDITERRANEAN", "SUEZ_NORTH", "BOSPHORUS_STRAIT",
            "BLACK_SEA_WEST", "BLACK_SEA"
    );
    private static final Set<String> RED_SEA_STATIC_NODES = Set.of(
            "SUEZ_SOUTH", "RED_SEA_NORTH", "RED_SEA_CENTRAL", "RED_SEA_SOUTH", "GULF_OF_ADEN"
    );
    private static final Set<String> INDIAN_STATIC_NODES = Set.of(
            "GULF_OF_OMAN", "ARABIAN_SEA", "SRI_LANKA_SOUTH", "BAY_OF_BENGAL_WEST", "BAY_OF_BENGAL",
            "BAY_OF_BENGAL_EAST", "INDIAN_OCEAN_CENTRAL", "INDIAN_OCEAN_EAST", "SOUTH_INDIAN_EAST",
            "MADAGASCAR_EAST", "MOZAMBIQUE_CHANNEL", "SOUTH_AFRICA_EAST", "CAPE_GOOD_HOPE"
    );
    private static final Set<String> EAST_ASIA_STATIC_NODES = Set.of(
            "JAPAN_EAST_APPROACH", "JAPAN_SOUTH_APPROACH", "EAST_CHINA_SEA_COAST", "TAIWAN_EAST_APPROACH",
            "PHILIPPINE_SEA_NORTH", "SOUTH_CHINA_SEA_NORTH", "VIETNAM_COAST", "ANDAMAN_SEA", "MALACCA_WEST",
            "MALACCA_STRAIT", "MALACCA_SOUTH", "SOUTH_CHINA_SEA", "EAST_CHINA_SEA", "JAVA_SEA", "CELEBES_SEA",
            "ARAFURA_SEA", "CORAL_SEA", "TASMAN_SEA", "AUSTRALIA_WEST", "AUSTRALIA_NORTH", "AUSTRALIA_EAST",
            "NEW_ZEALAND_NORTH", "NORTH_PACIFIC_WEST", "NORTH_PACIFIC_CENTRAL", "NORTH_PACIFIC_EAST"
    );

    private final GlobalFishingWatchClient client;
    private final GlobalFishingWatchProperties properties;
    private final MaritimeNetworkCatalog maritimeNetworkCatalog;
    private final GeoUtils geoUtils;
    private final MaritimeLandMask landMask;
    private final AtomicReference<MaritimeCorridorOverlay> cache = new AtomicReference<>();
    private final Object refreshMonitor = new Object();
    private CompletableFuture<MaritimeCorridorOverlay> inFlightRefresh;

    public GlobalFishingWatchCorridorOverlayProvider(GlobalFishingWatchClient client,
                                                     GlobalFishingWatchProperties properties,
                                                     MaritimeNetworkCatalog maritimeNetworkCatalog,
                                                     GeoUtils geoUtils,
                                                     MaritimeLandMask landMask) {
        this.client = client;
        this.properties = properties;
        this.maritimeNetworkCatalog = maritimeNetworkCatalog;
        this.geoUtils = geoUtils;
        this.landMask = landMask;
        this.cache.set(MaritimeCorridorOverlay.empty("GLOBAL_FISHING_WATCH"));
    }

    @Override
    public MaritimeCorridorOverlay currentOverlay() {
        return cache.get();
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

        for (GlobalFishingWatchProperties.RegionProperties region : properties.getRegions()) {
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
            connectRegionToStaticNetwork(region, regionNodes, encodedEdges);
        }

        connectCrossRegionNeighbors(nodesByRegion, encodedEdges);

        List<MaritimeNetworkCatalog.EdgeDefinition> overlayEdges = encodedEdges.stream()
                .filter(encoded -> isNavigableOverlayEdge(encoded, nodesById))
                .map(this::decodeEdge)
                .toList();

        logger.info("gfw.overlay.built nodes={} edges={} warnings={}", nodesById.size(), overlayEdges.size(), warnings.size());
        return new MaritimeCorridorOverlay(
                "GLOBAL_FISHING_WATCH",
                Instant.now(),
                List.copyOf(nodesById.values()),
                overlayEdges,
                List.copyOf(warnings)
        );
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

    private void connectRegionToStaticNetwork(GlobalFishingWatchProperties.RegionProperties region,
                                              List<MaritimeNode> regionNodes,
                                              LinkedHashSet<String> encodedEdges) {
        List<MaritimeNode> staticNodes = maritimeNetworkCatalog.coreNodes();
        for (MaritimeNode node : regionNodes) {
            staticNodes.stream()
                    .map(candidate -> new NodeDistance(candidate, geoUtils.calculateHaversineDistanceNm(node.getCoordinates(), candidate.getCoordinates())))
                    .filter(distance -> isBridgeCompatibleWithRegion(region.getId(), distance.node().getId()))
                    .filter(distance -> distance.distanceNm() <= maxBridgeDistanceNmForRegion(region.getId()))
                    .filter(distance -> hasStaticBridgeSupport(node, distance.node(), regionNodes))
                    .sorted(Comparator.comparingDouble(NodeDistance::distanceNm))
                    .limit(properties.getBridgeConnections())
                    .forEach(distance -> encodedEdges.add(encodeEdge(node.getId(), distance.node().getId())));
        }
    }

    private void connectCrossRegionNeighbors(Map<String, List<MaritimeNode>> nodesByRegion, LinkedHashSet<String> encodedEdges) {
        List<RegionNode> allNodes = nodesByRegion.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(node -> new RegionNode(entry.getKey(), node)))
                .toList();

        for (RegionNode current : allNodes) {
            allNodes.stream()
                    .filter(candidate -> !candidate.regionId().equals(current.regionId()))
                    .filter(candidate -> areCrossRegionNeighborsCompatible(current.regionId(), candidate.regionId()))
                    .filter(candidate -> !candidate.node().equals(current.node()))
                    .map(candidate -> new RegionNodeDistance(
                            candidate.regionId(),
                            candidate.node(),
                            geoUtils.calculateHaversineDistanceNm(current.node().getCoordinates(), candidate.node().getCoordinates())
                    ))
                    .filter(distance -> distance.distanceNm() <= maxCrossRegionDistanceNm(current.regionId(), distance.regionId()))
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
                    .sorted(Comparator.comparingDouble(RegionNodeDistance::distanceNm))
                    .limit(CROSS_REGION_CONNECTIONS)
                    .forEach(distance -> encodedEdges.add(encodeEdge(current.node().getId(), distance.node().getId())));
        }
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

    private boolean hasStaticBridgeSupport(MaritimeNode gfwNode, MaritimeNode staticNode, List<MaritimeNode> regionNodes) {
        double distanceNm = geoUtils.calculateHaversineDistanceNm(gfwNode.getCoordinates(), staticNode.getCoordinates());
        if (distanceNm <= BRIDGE_SUPPORT_RADIUS_NM) {
            return true;
        }

        List<Coordinates> sampledPath = geoUtils.densifyPath(List.of(gfwNode.getCoordinates(), staticNode.getCoordinates()), BRIDGE_SUPPORT_RADIUS_NM);
        if (sampledPath.size() <= 2) {
            return true;
        }

        for (int index = 1; index < sampledPath.size() - 1; index++) {
            Coordinates sample = sampledPath.get(index);
            boolean supported = regionNodes.stream()
                    .filter(node -> !node.equals(gfwNode))
                    .anyMatch(node -> geoUtils.calculateHaversineDistanceNm(sample, node.getCoordinates()) <= BRIDGE_SUPPORT_RADIUS_NM);
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

    private boolean isBridgeCompatibleWithRegion(String regionId, String staticNodeId) {
        Set<String> allowedNodes = allowedStaticNodesForRegion(regionId);
        return allowedNodes.isEmpty() || allowedNodes.contains(staticNodeId);
    }

    private Set<String> allowedStaticNodesForRegion(String regionId) {
        Set<String> allowed = new HashSet<>();
        if (regionId.startsWith("north-pacific") || regionId.startsWith("south-pacific")) {
            allowed.addAll(PACIFIC_STATIC_NODES);
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("peru-ecuador-coast")) {
            allowed.addAll(Set.of(
                    "PACIFIC_SOUTH_EAST", "PACIFIC_TROPICAL_EAST", "PANAMA_PACIFIC_OUTER", "PANAMA_PACIFIC"
            ));
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("atlantic-")) {
            allowed.addAll(ATLANTIC_STATIC_NODES);
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("caribbean-panama-west")) {
            allowed.addAll(Set.of(
                    "PANAMA_ATLANTIC", "PANAMA_CARIBBEAN_OUTER", "CARIBBEAN_SW", "CARIBBEAN_WEST", "CARIBBEAN_COLOMBIA"
            ));
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("caribbean-panama-east")) {
            allowed.addAll(ATLANTIC_STATIC_NODES);
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("mediterranean-")) {
            allowed.addAll(MEDITERRANEAN_STATIC_NODES);
            allowed.add("GIBRALTAR_STRAIT");
            allowed.add("GIBRALTAR_WEST");
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("iberia-atlantic-approach")) {
            allowed.addAll(Set.of(
                    "GIBRALTAR_WEST", "IBERIA_WEST", "PORTUGAL_APPROACH",
                    "MADEIRA_APPROACH", "AZORES_SOUTH", "AZORES_CORRIDOR"
            ));
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("iberia-mediterranean-approach")) {
            allowed.addAll(Set.of(
                    "GIBRALTAR_WEST", "GIBRALTAR_STRAIT", "ALBORAN_SEA",
                    "WEST_MEDITERRANEAN", "IBERIA_WEST"
            ));
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("suez-north")) {
            allowed.addAll(MEDITERRANEAN_STATIC_NODES);
            allowed.addAll(RED_SEA_STATIC_NODES);
            allowed.add("SUEZ_CANAL");
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("red-sea-")) {
            allowed.addAll(RED_SEA_STATIC_NODES);
            allowed.add("SUEZ_CANAL");
            allowed.add("SUEZ_NORTH");
            allowed.add("GULF_OF_OMAN");
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("indian-ocean-")) {
            allowed.addAll(INDIAN_STATIC_NODES);
            allowed.add("GULF_OF_ADEN");
            allowed.add("RED_SEA_SOUTH");
            allowed.add("MALACCA_WEST");
            allowed.add("MALACCA_STRAIT");
            allowed.add("MALACCA_SOUTH");
            return Set.copyOf(allowed);
        }
        if (regionId.startsWith("east-asia-")) {
            allowed.addAll(EAST_ASIA_STATIC_NODES);
            allowed.addAll(INDIAN_STATIC_NODES);
            return Set.copyOf(allowed);
        }
        return Set.of();
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
        MaritimeNode fromNode = resolveOverlayOrStaticNode(edge.fromNodeId(), overlayNodesById);
        MaritimeNode toNode = resolveOverlayOrStaticNode(edge.toNodeId(), overlayNodesById);
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

    private double maxBridgeDistanceNmForRegion(String regionId) {
        if (regionId.startsWith("peru-ecuador-coast")) {
            return 280.0;
        }
        if (regionId.startsWith("caribbean-panama-west") || regionId.startsWith("caribbean-panama-east")) {
            return 240.0;
        }
        if (regionId.startsWith("iberia-atlantic-approach") || regionId.startsWith("iberia-mediterranean-approach")) {
            return 220.0;
        }
        return properties.getMaxBridgeDistanceNm();
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

    private MaritimeNode resolveOverlayOrStaticNode(String nodeId, Map<String, MaritimeNode> overlayNodesById) {
        MaritimeNode overlayNode = overlayNodesById.get(nodeId);
        if (overlayNode != null) {
            return overlayNode;
        }
        return maritimeNetworkCatalog.findNode(nodeId).orElse(null);
    }

    private String formatCoord(double value) {
        return Normalizer.normalize(String.format(Locale.US, "%.2f", value), Normalizer.Form.NFD)
                .replace('.', '_')
                .replace('-', 'm');
    }

    private record NodeDistance(MaritimeNode node, double distanceNm) {
    }

    private record RegionNode(String regionId, MaritimeNode node) {
    }

    private record RegionNodeDistance(String regionId, MaritimeNode node, double distanceNm) {
    }

    private record BucketCoordinate(int latBucket, int lonBucket) {
    }
}
