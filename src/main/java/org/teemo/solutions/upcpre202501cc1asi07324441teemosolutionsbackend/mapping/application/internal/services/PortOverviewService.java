package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.PortOperationalStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PortOverviewService {

    private static final Logger log = LoggerFactory.getLogger(PortOverviewService.class);

    private final List<PortSnapshot> cache = new ArrayList<>();
    private Instant lastSyncedAt = Instant.now();

    private final ObjectMapper mapper;
    private final PortService portService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Path runtimeCacheFile = Path.of("cache", "port_overview_cache.json");

    @Value("${ports.overpass.url:https://overpass-api.de/api/interpreter}")
    private String overpassUrl;

    public PortOverviewService(ObjectMapper mapper, PortService portService) {
        this.mapper = mapper;
        this.portService = portService;
    }

    @PostConstruct
    public void init() {
        if (refreshFromOverpass()) {
            return;
        }
        if (loadRuntimeCache()) {
            log.warn("Using cached port overview data (Overpass unavailable)");
            return;
        }
        refreshFromPorts();
    }

    public PortOverviewResult overview(PortOperationalStatus statusFilter, int page, int size) {
        List<PortSnapshot> filtered = statusFilter == null
                ? cache
                : cache.stream()
                .filter(snapshot -> snapshot.status() == statusFilter)
                .toList();
        int total = filtered.size();
        if (total == 0) {
            return new PortOverviewResult(Collections.emptyList(), total, lastSyncedAt);
        }
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return new PortOverviewResult(filtered.subList(from, to), total, lastSyncedAt);
    }

    private boolean refreshFromOverpass() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("data", buildOverpassQuery());
            String payload = restTemplate.postForObject(overpassUrl, new HttpEntity<>(body, headers), String.class);
            if (payload == null) {
                return false;
            }
            JsonNode root = mapper.readTree(payload);
            JsonNode elements = root.path("elements");
            if (!elements.isArray() || elements.isEmpty()) {
                return false;
            }
            cache.clear();
            for (JsonNode element : elements) {
                JsonNode tags = element.path("tags");
                if (tags.isMissingNode()) continue;
                String name = firstNonEmpty(tags, "name", "harbour:name", "seamark:name");
                if (name == null) continue;
                String id = element.path("type").asText() + ":" + element.path("id").asText();
                double lat = element.has("lat") ? element.get("lat").asDouble()
                        : element.path("center").path("lat").asDouble();
                double lon = element.has("lon") ? element.get("lon").asDouble()
                        : element.path("center").path("lon").asDouble();
                String country = firstNonEmpty(tags, "addr:country", "is_in:country_code", "country", "ISO3166-1:alpha2");
                if (country == null) {
                    country = "--";
                }
                String phone = firstNonEmpty(tags, "contact:phone", "phone");
                String email = firstNonEmpty(tags, "contact:email", "email");
                String website = firstNonEmpty(tags, "contact:website", "website", "url");
                PortOperationalStatus status = deriveStatusFromTags(tags);
                String reason = deriveReason(status, explainStatus(tags));
                int traffic = deriveTrafficFromTags(tags, id);
                cache.add(new PortSnapshot(
                        id,
                        name,
                        country,
                        lat,
                        lon,
                        status,
                        reason,
                        traffic,
                        Instant.now(),
                        phone,
                        email,
                        website
                ));
            }
            if (cache.isEmpty()) {
                return false;
            }
            lastSyncedAt = Instant.now();
            persistRuntimeCache();
            log.info("Port overview refreshed from Overpass with {} entries", cache.size());
            return true;
        } catch (Exception ex) {
            log.error("Unable to refresh port overview from Overpass", ex);
            return false;
        }
    }

    private boolean loadRuntimeCache() {
        if (!Files.exists(runtimeCacheFile)) {
            return false;
        }
        try {
            JsonNode root = mapper.readTree(runtimeCacheFile.toFile());
            cache.clear();
            for (JsonNode node : root.path("ports")) {
                cache.add(new PortSnapshot(
                        node.path("portId").asText(),
                        node.path("name").asText(),
                        node.path("country").asText(),
                        node.path("lat").asDouble(),
                        node.path("lon").asDouble(),
                        PortOperationalStatus.valueOf(node.path("status").asText()),
                        node.path("reason").isNull() ? null : node.path("reason").asText(),
                        node.path("traffic").asInt(),
                        node.hasNonNull("updatedAt") ? Instant.parse(node.get("updatedAt").asText()) : null,
                        node.hasNonNull("contactPhone") ? node.get("contactPhone").asText() : null,
                        node.hasNonNull("contactEmail") ? node.get("contactEmail").asText() : null,
                        node.hasNonNull("website") ? node.get("website").asText() : null
                ));
            }
            if (root.hasNonNull("lastSyncedAt")) {
                lastSyncedAt = Instant.parse(root.get("lastSyncedAt").asText());
            }
            return !cache.isEmpty();
        } catch (IOException e) {
            log.warn("Unable to read runtime port overview cache", e);
            return false;
        }
    }

    private boolean refreshFromPorts() {
        List<Port> ports = portService.getAllPorts();
        if (ports.isEmpty()) {
            log.warn("No local ports available to build overview dataset");
            return false;
        }
        cache.clear();
        lastSyncedAt = Instant.now();
        for (Port port : ports) {
            String key = port.getId() != null ? port.getId() : port.getName().toUpperCase().replace(" ", "_");
            int hash = Math.abs(key.hashCode());
            PortOperationalStatus status = deriveStatusFromHash(hash);
            cache.add(new PortSnapshot(
                    key,
                    port.getName(),
                    port.getContinent(),
                    port.getCoordinates().latitude(),
                    port.getCoordinates().longitude(),
                    status,
                    deriveReason(status, "Datos internos heurísticos"),
                    deriveTraffic(hash),
                    deriveUpdatedAt(hash),
                    null,
                    null,
                    null
            ));
        }
        log.info("Port overview built from internal dataset with {} entries", cache.size());
        return true;
    }

    private void persistRuntimeCache() {
        try {
            Files.createDirectories(runtimeCacheFile.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("lastSyncedAt", lastSyncedAt.toString());
            root.put("ports", cache.stream().map(snapshot -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("portId", snapshot.portId());
                entry.put("name", snapshot.name());
                entry.put("country", snapshot.country());
                entry.put("lat", snapshot.lat());
                entry.put("lon", snapshot.lon());
                entry.put("status", snapshot.status().name());
                entry.put("reason", snapshot.reason());
                entry.put("traffic", snapshot.traffic());
                entry.put("updatedAt", snapshot.updatedAt() != null ? snapshot.updatedAt().toString() : null);
                entry.put("contactPhone", snapshot.contactPhone());
                entry.put("contactEmail", snapshot.contactEmail());
                entry.put("website", snapshot.website());
                return entry;
            }).collect(Collectors.toList()));
            mapper.writerWithDefaultPrettyPrinter().writeValue(runtimeCacheFile.toFile(), root);
        } catch (IOException e) {
            log.warn("Unable to persist port overview runtime cache", e);
        }
    }

    private String buildOverpassQuery() {
        return """
                [out:json][timeout:60];
                (
                  node["harbour"]["contact:phone"](-60,-180,85,180);
                  node["harbour"]["phone"](-60,-180,85,180);
                  way["harbour"]["contact:phone"](-60,-180,85,180);
                  way["harbour"]["phone"](-60,-180,85,180);
                  relation["harbour"]["contact:phone"](-60,-180,85,180);
                );
                out center 400;
                """;
    }

    private PortOperationalStatus deriveStatusFromTags(JsonNode tags) {
        if (isTrue(tags, "disused") || isTrue(tags, "abandoned")) {
            return PortOperationalStatus.CLOSED;
        }
        String access = firstNonEmpty(tags, "access");
        if (access != null && Set.of("no", "private", "military").contains(access.toLowerCase())) {
            return PortOperationalStatus.RESTRICTED;
        }
        return PortOperationalStatus.OPEN;
    }

    private PortOperationalStatus deriveStatusFromHash(int hash) {
        int mod = hash % 10;
        if (mod == 0) return PortOperationalStatus.CLOSED;
        if (mod <= 3) return PortOperationalStatus.RESTRICTED;
        return PortOperationalStatus.OPEN;
    }

    private String deriveReason(PortOperationalStatus status, String detail) {
        if (status == PortOperationalStatus.OPEN) return null;
        return detail != null ? detail : (status == PortOperationalStatus.RESTRICTED
                ? "Operaciones restringidas segun dataset"
                : "Puerto cerrado segun dataset");
    }

    private String explainStatus(JsonNode tags) {
        if (isTrue(tags, "disused") || isTrue(tags, "abandoned")) {
            return "Marcado como disused/abandoned en OSM";
        }
        String access = firstNonEmpty(tags, "access");
        if (access != null) {
            return "Access=" + access;
        }
        return null;
    }

    private boolean isTrue(JsonNode tags, String key) {
        JsonNode value = tags.path(key);
        return !value.isMissingNode() && "yes".equalsIgnoreCase(value.asText());
    }

    private int deriveTrafficFromTags(JsonNode tags, String seed) {
        JsonNode sizeNode = tags.path("harbour:size");
        if (!sizeNode.isMissingNode()) {
            try {
                int size = Integer.parseInt(sizeNode.asText());
                return switch (size) {
                    case 1 -> 150;
                    case 2 -> 300;
                    case 3 -> 520;
                    case 4 -> 720;
                    case 5 -> 900;
                    default -> 200 + size * 50;
                };
            } catch (NumberFormatException ignored) {
            }
        }
        return deriveTraffic(seed.hashCode());
    }

    private int deriveTraffic(int hash) {
        return 150 + Math.abs(hash % 900);
    }

    private Instant deriveUpdatedAt(int hash) {
        long hoursAgo = Math.abs(hash % 72);
        return lastSyncedAt.minusSeconds(hoursAgo * 3600L);
    }

    private String firstNonEmpty(JsonNode tags, String... keys) {
        for (String key : keys) {
            JsonNode node = tags.path(key);
            if (!node.isMissingNode() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    public record PortSnapshot(
            String portId,
            String name,
            String country,
            double lat,
            double lon,
            PortOperationalStatus status,
            String reason,
            int traffic,
            Instant updatedAt,
            String contactPhone,
            String contactEmail,
            String website
    ) {}

    public record PortOverviewResult(
            List<PortSnapshot> content,
            long totalElements,
            Instant lastSyncedAt
    ) {}
}
