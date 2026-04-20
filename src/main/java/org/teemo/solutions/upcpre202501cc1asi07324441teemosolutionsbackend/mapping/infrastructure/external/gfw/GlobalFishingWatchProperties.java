package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.external.gfw;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "routing.maritime.gfw")
public class GlobalFishingWatchProperties {
    private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private boolean enabled = false;
    private String baseUrl = "https://gateway.api.globalfishingwatch.org";
    private String apiToken = "";
    private Duration timeout = Duration.ofSeconds(5);
    private String startDate = LocalDate.now().minusMonths(6).toString();
    private String endDate = LocalDate.now().minusDays(4).toString();
    private String spatialResolution = "LOW";
    private String temporalResolution = "ENTIRE";
    private int maxCellsPerRegion = 18;
    private int nearestNeighbors = 3;
    private int bridgeConnections = 2;
    private double minHours = 120.0;
    private int minVesselIds = 2;
    private double maxNeighborDistanceNm = 900.0;
    private double maxBridgeDistanceNm = 650.0;
    private List<String> vesselTypes = new ArrayList<>(List.of("cargo"));
    private List<RegionProperties> regions = defaultRegions();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl;
        }
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken == null ? "" : apiToken.trim();
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        if (timeout != null) {
            this.timeout = timeout;
        }
    }

    public LocalDate getStartDate() {
        return parseDate(startDate, LocalDate.now().minusMonths(6));
    }

    public void setStartDate(String startDate) {
        if (startDate != null && !startDate.isBlank()) {
            this.startDate = sanitizeDate(startDate);
        }
    }

    public LocalDate getEndDate() {
        return parseDate(endDate, LocalDate.now().minusDays(4));
    }

    public void setEndDate(String endDate) {
        if (endDate != null && !endDate.isBlank()) {
            this.endDate = sanitizeDate(endDate);
        }
    }

    public String getSpatialResolution() {
        return spatialResolution;
    }

    public void setSpatialResolution(String spatialResolution) {
        if (spatialResolution != null && !spatialResolution.isBlank()) {
            this.spatialResolution = spatialResolution;
        }
    }

    public String getTemporalResolution() {
        return temporalResolution;
    }

    public void setTemporalResolution(String temporalResolution) {
        if (temporalResolution != null && !temporalResolution.isBlank()) {
            this.temporalResolution = temporalResolution;
        }
    }

    public int getMaxCellsPerRegion() {
        return maxCellsPerRegion;
    }

    public void setMaxCellsPerRegion(int maxCellsPerRegion) {
        this.maxCellsPerRegion = Math.max(1, maxCellsPerRegion);
    }

    public int getNearestNeighbors() {
        return nearestNeighbors;
    }

    public void setNearestNeighbors(int nearestNeighbors) {
        this.nearestNeighbors = Math.max(1, nearestNeighbors);
    }

    public int getBridgeConnections() {
        return bridgeConnections;
    }

    public void setBridgeConnections(int bridgeConnections) {
        this.bridgeConnections = Math.max(1, bridgeConnections);
    }

    public double getMinHours() {
        return minHours;
    }

    public void setMinHours(double minHours) {
        this.minHours = Math.max(0.0, minHours);
    }

    public int getMinVesselIds() {
        return minVesselIds;
    }

    public void setMinVesselIds(int minVesselIds) {
        this.minVesselIds = Math.max(1, minVesselIds);
    }

    public double getMaxNeighborDistanceNm() {
        return maxNeighborDistanceNm;
    }

    public void setMaxNeighborDistanceNm(double maxNeighborDistanceNm) {
        this.maxNeighborDistanceNm = Math.max(1.0, maxNeighborDistanceNm);
    }

    public double getMaxBridgeDistanceNm() {
        return maxBridgeDistanceNm;
    }

    public void setMaxBridgeDistanceNm(double maxBridgeDistanceNm) {
        this.maxBridgeDistanceNm = Math.max(1.0, maxBridgeDistanceNm);
    }

    public List<String> getVesselTypes() {
        return vesselTypes;
    }

    public void setVesselTypes(List<String> vesselTypes) {
        if (vesselTypes != null && !vesselTypes.isEmpty()) {
            this.vesselTypes = vesselTypes.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .distinct()
                    .toList();
        }
    }

    public List<RegionProperties> getRegions() {
        return regions;
    }

    public void setRegions(List<RegionProperties> regions) {
        if (regions != null && !regions.isEmpty()) {
            this.regions = new ArrayList<>(regions);
        }
    }

    public boolean isConfigured() {
        return enabled && !apiToken.isBlank();
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        try {
            return LocalDate.parse(sanitizeDate(value), ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String sanitizeDate(String value) {
        return value == null ? "" : value.trim().replace('\u00A0', ' ');
    }

    private static List<RegionProperties> defaultRegions() {
        return List.of(
                new RegionProperties("north-pacific-west", "North Pacific West Corridor", 20.0, 135.0, 42.0, 179.0),
                new RegionProperties("north-pacific-east", "North Pacific East Corridor", 20.0, -179.0, 42.0, -120.0),
                new RegionProperties("south-pacific-west", "South Pacific West Corridor", -42.0, 145.0, -8.0, 179.0),
                new RegionProperties("south-pacific-east", "South Pacific East Corridor", -42.0, -179.0, -8.0, -95.0),
                new RegionProperties("east-asia-south-west", "East Asia Southern Westbound Lanes", 1.0, 102.0, 19.0, 112.0),
                new RegionProperties("east-asia-south-east", "East Asia Southern Eastbound Lanes", 1.0, 112.0, 21.0, 123.0),
                new RegionProperties("east-asia-north-west", "East Asia Northern Westbound Lanes", 19.0, 112.0, 30.0, 123.0),
                new RegionProperties("east-asia-north-east", "East Asia Northern Eastbound Lanes", 19.0, 123.0, 36.0, 135.0),
                new RegionProperties("indian-ocean-west-south", "Indian Ocean Southwest Lanes", -35.0, 38.0, -10.0, 58.0),
                new RegionProperties("indian-ocean-west-north", "Indian Ocean Northwest Lanes", -10.0, 45.0, 10.0, 72.0),
                new RegionProperties("indian-ocean-east-south", "Indian Ocean Southeast Lanes", -25.0, 72.0, -2.0, 90.0),
                new RegionProperties("indian-ocean-east-north", "Indian Ocean Northeast Lanes", -2.0, 80.0, 18.0, 105.0),
                new RegionProperties("red-sea-south", "Red Sea South and Bab-el-Mandeb", 10.0, 41.5, 16.5, 49.5),
                new RegionProperties("red-sea-central", "Red Sea Central Lanes", 16.0, 34.0, 24.5, 43.0),
                new RegionProperties("suez-north", "Suez and East Mediterranean Gateway", 24.0, 28.0, 33.5, 35.5),
                new RegionProperties("mediterranean-east", "East Mediterranean Lanes", 31.0, 20.0, 37.5, 31.5),
                new RegionProperties("mediterranean-central", "Central Mediterranean Lanes", 33.0, 9.0, 40.5, 20.5),
                new RegionProperties("mediterranean-west", "West Mediterranean and Alboran", 33.0, -8.5, 41.5, 10.0),
                new RegionProperties("iberia-atlantic-approach", "Iberia Atlantic Approach", 31.0, -15.0, 41.5, -5.0),
                new RegionProperties("iberia-mediterranean-approach", "Iberia Mediterranean Approach", 35.0, -6.0, 41.5, 2.5),
                new RegionProperties("atlantic-north-west", "North Atlantic Western Lanes", 10.0, -85.0, 30.0, -60.0),
                new RegionProperties("atlantic-north-central-west", "North Atlantic Central Western Lanes", 18.0, -60.0, 38.0, -38.0),
                new RegionProperties("atlantic-north-central-east", "North Atlantic Central Eastern Lanes", 24.0, -38.0, 44.0, -16.0),
                new RegionProperties("atlantic-north-east", "North Atlantic Eastern Lanes", 30.0, -16.0, 58.0, 12.0),
                new RegionProperties("atlantic-south-west", "South Atlantic Western Lanes", -58.0, -70.0, -24.0, -44.0),
                new RegionProperties("atlantic-south-central", "South Atlantic Central Lanes", -45.0, -44.0, -8.0, -18.0),
                new RegionProperties("atlantic-south-east-west", "South Atlantic Eastern Westbound Lanes", -32.0, -18.0, -4.0, 2.0),
                new RegionProperties("atlantic-south-east-east", "South Atlantic Eastern Eastbound Lanes", -24.0, 2.0, 12.0, 20.0),
                new RegionProperties("peru-ecuador-coast", "Peru and Ecuador Coastal Corridor", -14.5, -86.0, 6.5, -76.0),
                new RegionProperties("caribbean-panama-west", "Panama and Western Caribbean", 5.0, -90.0, 19.0, -77.0),
                new RegionProperties("caribbean-panama-east", "Central and Eastern Caribbean", 11.0, -77.0, 27.0, -58.0)
        );
    }

    public static class RegionProperties {
        private String id;
        private String name;
        private double minLat;
        private double minLon;
        private double maxLat;
        private double maxLon;

        public RegionProperties() {
        }

        public RegionProperties(String id, String name, double minLat, double minLon, double maxLat, double maxLon) {
            this.id = id;
            this.name = name;
            this.minLat = minLat;
            this.minLon = minLon;
            this.maxLat = maxLat;
            this.maxLon = maxLon;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getMinLat() {
            return minLat;
        }

        public void setMinLat(double minLat) {
            this.minLat = minLat;
        }

        public double getMinLon() {
            return minLon;
        }

        public void setMinLon(double minLon) {
            this.minLon = minLon;
        }

        public double getMaxLat() {
            return maxLat;
        }

        public void setMaxLat(double maxLat) {
            this.maxLat = maxLat;
        }

        public double getMaxLon() {
            return maxLon;
        }

        public void setMaxLon(double maxLon) {
            this.maxLon = maxLon;
        }
    }
}
