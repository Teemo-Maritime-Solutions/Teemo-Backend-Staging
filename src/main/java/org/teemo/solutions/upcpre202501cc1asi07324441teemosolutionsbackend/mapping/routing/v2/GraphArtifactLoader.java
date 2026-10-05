package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Local-only bounded reader. A manifest alone is not proof of operational validation. */
public final class GraphArtifactLoader {
    private final ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final long maxEntryBytes;
    public GraphArtifactLoader(long maxEntryBytes) {
        if (maxEntryBytes < 1024 || maxEntryBytes > 2_000_000_000L) throw new IllegalArgumentException("Invalid entry size limit");
        this.maxEntryBytes = maxEntryBytes;
    }

    public PhysicalGraph load(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Set<String> names = new HashSet<>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!names.add(entry.getName()) || entry.getSize() < 0 || entry.getSize() > maxEntryBytes)
                    throw new IOException("Duplicate or oversized ZIP entry");
            }
            ZipEntry manifestEntry = requireEntry(zip, "manifest.json");
            if (manifestEntry.getSize() > 2_000_000) throw new IOException("Oversized manifest");
            JsonNode manifest;
            try (InputStream input = zip.getInputStream(manifestEntry)) { manifest = mapper.readTree(input); }
            int schema = manifest.path("schemaVersion").asInt(-1);
            if ((schema != 1 && schema != 2) || !manifest.path("graphVersion").asText().matches("[a-f0-9]{64}"))
                throw new IOException("Unsupported/missing artifact version");
            if (schema == 1 && manifest.has("regionalControls")) throw new IOException("Regional controls require schema 2");
            if (!"RESEARCH_NOT_NAVIGATION".equals(manifest.path("purpose").asText()))
                throw new IOException("Unsupported artifact purpose");
            double step = requiredDouble(manifest.path("parameters"), "geodesic_step_m");
            if (step <= 0 || step > 10000) throw new IOException("Invalid geometry engineering parameters");
            Set<String> sources = new HashSet<>();
            for (JsonNode source : manifest.path("sources")) {
                for (String field : List.of("id", "url", "license", "licenseUrl", "version", "acquiredAt", "unit", "coverage", "sha256"))
                    requiredText(source, field);
                Instant.parse(source.path("acquiredAt").asText());
                for (String field : List.of("url", "licenseUrl")) {
                    String url = source.path(field).asText();
                    java.net.URI uri = java.net.URI.create(url);
                    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                            || url.toLowerCase(Locale.ROOT).matches(".*[?&](token|api_key|apikey|secret|password|authorization)=.*"))
                        throw new IOException("Invalid/private source URL");
                }
                if (!source.path("limitations").isArray() || source.path("limitations").isEmpty()
                        || !"SOURCE_DATA".equals(source.path("status").asText())
                        || !source.path("sha256").asText().matches("[a-f0-9]{64}") || !sources.add(source.path("id").asText()))
                    throw new IOException("Invalid/duplicate source provenance");
            }
            if (sources.isEmpty()) throw new IOException("No provenance registry");
            JsonNode control = manifest.path("regionalControls");
            if (schema == 2) {
                if (!"GLOBAL_REGIONAL_RESEARCH_V1".equals(manifest.path("validationProfile").asText())
                        || !"SOURCE_COVERAGE_PRIORITY_V1".equals(control.path("policy").asText())
                        || !"NOAA_NEW_YORK_V2".equals(control.path("regionId").asText())
                        || !"regional-evidence.jsonl".equals(control.path("evidenceTable").asText())
                        || !sources.contains(requiredText(control, "chartSourceId")))
                    throw new IOException("Missing/unsupported regional control contract");
                for (String field : List.of("chartSourceSha256", "regionalGraphVersion", "regionalArtifactSha256",
                        "globalArtifactSha256", "globalGraphVersion", "globalValidationSha256", "builderSha256", "policySha256"))
                    if (!control.path(field).asText().matches("[a-f0-9]{64}")) throw new IOException("Missing regional binding: " + field);
                boolean matchingSource = false;
                for (JsonNode source : manifest.path("sources"))
                    if (source.path("id").equals(control.path("chartSourceId")))
                        matchingSource = source.path("sha256").equals(control.path("chartSourceSha256"));
                if (!matchingSource) throw new IOException("Regional source digest mismatch");
                Set<String> featureIds = new HashSet<>();
                readTable(zip, manifest, "regional-evidence.jsonl", row -> {
                    if (!references(row, sources).contains(control.path("chartSourceId").asText())
                            || !featureIds.add(requiredText(row, "id")) || !row.path("geometry").isObject())
                        throw new IllegalArgumentException("Invalid regional evidence");
                });
                if (featureIds.isEmpty()) throw new IOException("Empty regional evidence");
            }
            List<PhysicalGraph.Node> nodes = new ArrayList<>();
            readTable(zip, manifest, "nodes.jsonl", n -> nodes.add(new PhysicalGraph.Node(requiredText(n, "id"),
                    point(n.path("point")), requiredText(n, "kind"), references(n, sources))));
            List<PhysicalGraph.Edge> edges = new ArrayList<>();
            readTable(zip, manifest, "edges.jsonl", e -> {
                if (!"WGS84_GEODESIC".equals(requiredText(e, "geometryModel"))) throw new IllegalArgumentException("Unknown geometry model");
                List<PhysicalGraph.Point> geometry = new ArrayList<>();
                e.path("geometry").forEach(p -> geometry.add(point(p)));
                List<String> regionalIds = e.has("regionalControlIds") ? strings(e.path("regionalControlIds")) : List.of();
                if (schema == 1 && !regionalIds.isEmpty()) throw new IllegalArgumentException("Undeclared regional control");
                if (schema == 2 && (!regionalIds.isEmpty() && !regionalIds.equals(List.of(control.path("regionId").asText()))
                        || references(e, sources).contains(control.path("chartSourceId").asText()) != !regionalIds.isEmpty()))
                    throw new IllegalArgumentException("Edge regional control/source mismatch");
                edges.add(new PhysicalGraph.Edge(edges.size(), requiredInt(e, "fromNode"), requiredInt(e, "toNode"),
                        requiredDouble(e, "distanceM"), geometry, nullableDouble(e, "minimumDepthM"),
                        requiredText(e, "kind"), e.path("specialZone").isNull() ? null : requiredText(e, "specialZone"),
                        new HashSet<>(strings(e.path("restrictions"))), references(e, sources), requiredText(e, "legalStatus"), regionalIds));
            });
            List<PhysicalGraph.Port> ports = new ArrayList<>();
            readTable(zip, manifest, "ports.jsonl", p -> ports.add(new PhysicalGraph.Port(requiredText(p, "id"),
                    requiredText(p, "name"), p.path("point").isNull() ? null : point(p.path("point")),
                    p.path("nodeId").isNull() ? null : requiredInt(p, "nodeId"), requiredText(p, "status"), references(p, sources))));
            if (nodes.size() != manifest.path("report").path("nodes").asInt(-1)
                    || edges.size() != manifest.path("report").path("directedEdges").asInt(-1))
                throw new IOException("Artifact counts mismatch");
            if (schema == 2 && edges.stream().filter(e -> !e.regionalControlIds().isEmpty()).count()
                    != manifest.path("report").path("integrationCounts").path("regionalControlledEdges").asLong(-1))
                throw new IOException("Regional edge coverage count mismatch");
            if (manifest.has("panamaControls")) {
                readTable(zip, manifest, "panama-evidence.jsonl", row -> {
                    if (!references(row, sources).contains("osm-panama")
                            || !row.path("sourceSha256").equals(manifest.path("panamaControls").path("sourceSha256")))
                        throw new IllegalArgumentException("Panama source evidence mismatch");
                });
            }
            if (manifest.has("suezControls")) {
                readTable(zip, manifest, "suez-evidence.jsonl", row -> {
                    if (!references(row, sources).contains("osm-egypt-suez")
                            || !row.path("sourceSha256").equals(manifest.path("suezControls").path("sourceSha256")))
                        throw new IllegalArgumentException("Suez source evidence mismatch");
                });
            }
            return new PhysicalGraph(nodes, edges, ports, manifest);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid graph artifact: " + e.getMessage(), e); }
    }

    private void readTable(ZipFile zip, JsonNode manifest, String name, Consumer<JsonNode> reader) throws IOException {
        String expected = manifest.path("tables").path(name).asText();
        if (!expected.matches("[a-f0-9]{64}")) throw new IOException("Missing table checksum");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ZipEntry entry = requireEntry(zip, name);
            try (DigestInputStream input = new DigestInputStream(zip.getInputStream(entry), digest);
                 BufferedReader lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line; long bytes = 0;
                while ((line = lines.readLine()) != null) {
                    bytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
                    if (bytes > maxEntryBytes || line.length() > 1_000_000) throw new IOException("Table size limit exceeded");
                    JsonNode row = mapper.readTree(line);
                    if (row == null || !row.isObject()) throw new IOException("Invalid JSON-lines record");
                    reader.accept(row);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(expected)) throw new IOException("Table checksum mismatch: " + name);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ZipEntry requireEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() > maxEntryBytes) throw new IOException("Missing/oversized entry: " + name);
        return entry;
    }
    private static List<String> references(JsonNode row, Set<String> sources) {
        List<String> ids = strings(row.path("sourceIds"));
        if (ids.isEmpty() || !sources.containsAll(ids)) throw new IllegalArgumentException("Unknown source reference");
        return ids;
    }
    private static List<String> strings(JsonNode array) {
        if (!array.isArray()) throw new IllegalArgumentException("Expected array");
        List<String> result = new ArrayList<>();
        array.forEach(n -> { if (!n.isTextual() || n.asText().isBlank()) throw new IllegalArgumentException("Expected text"); result.add(n.asText()); });
        return result;
    }
    private static PhysicalGraph.Point point(JsonNode value) {
        if (!value.isArray() || value.size() != 2 || !value.get(0).isNumber() || !value.get(1).isNumber())
            throw new IllegalArgumentException("Invalid point array");
        return new PhysicalGraph.Point(value.get(0).asDouble(), value.get(1).asDouble());
    }
    private static String requiredText(JsonNode n, String field) {
        if (!n.path(field).isTextual() || n.path(field).asText().isBlank()) throw new IllegalArgumentException("Missing " + field);
        return n.path(field).asText();
    }
    private static int requiredInt(JsonNode n, String field) {
        if (!n.path(field).isIntegralNumber() || !n.path(field).canConvertToInt()) throw new IllegalArgumentException("Missing integer " + field);
        return n.path(field).asInt();
    }
    private static double requiredDouble(JsonNode n, String field) {
        if (!n.path(field).isNumber() || !Double.isFinite(n.path(field).asDouble())) throw new IllegalArgumentException("Missing finite " + field);
        return n.path(field).asDouble();
    }
    private static Double nullableDouble(JsonNode n, String field) {
        if (!n.has(field)) throw new IllegalArgumentException("Missing nullable field " + field);
        return n.get(field).isNull() ? null : requiredDouble(n, field);
    }
}
