package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic archives generated only in JUnit's temporary test directory. */
class GraphArtifactLoaderTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    private Path artifact(String variant) throws Exception {
        var graph = SyntheticGraphFixture.graph();
        StringBuilder nodeLines = new StringBuilder(), edgeLines = new StringBuilder();
        for (var node : graph.nodes()) {
            var row = mapper.createObjectNode().put("id", node.id()).put("kind", "SYNTHETIC");
            row.putArray("point").add(node.point().longitude()).add(node.point().latitude()); row.putArray("sourceIds").add("fixture");
            nodeLines.append(mapper.writeValueAsString(row)).append('\n');
        }
        for (var edge : graph.edges()) {
            var row = mapper.createObjectNode().put("fromNode", edge.from()).put("toNode", edge.to()).put("distanceM", edge.distanceM())
                    .put("kind", "SYNTHETIC").put("geometryModel", "WGS84_GEODESIC").put("legalStatus", "UNKNOWN");
            row.putNull("minimumDepthM"); row.putNull("specialZone"); row.putArray("restrictions"); row.putArray("sourceIds").add("fixture");
            if (variant.startsWith("regional")) row.putArray("regionalControlIds").add("NOAA_NEW_YORK_V2");
            var geometry = row.putArray("geometry"); edge.geometry().forEach(p -> geometry.addArray().add(p.longitude()).add(p.latitude()));
            edgeLines.append(mapper.writeValueAsString(row)).append('\n');
        }
        Map<String, byte[]> tables = new TreeMap<>();
        tables.put("nodes.jsonl", nodeLines.toString().getBytes(StandardCharsets.UTF_8));
        tables.put("edges.jsonl", edgeLines.toString().getBytes(StandardCharsets.UTF_8)); tables.put("ports.jsonl", new byte[0]);
        ObjectNode manifest = mapper.createObjectNode().put("schemaVersion", 1).put("graphVersion", "a".repeat(64)).put("purpose", "RESEARCH_NOT_NAVIGATION");
        manifest.putObject("parameters").put("geodesic_step_m", 500);
        manifest.putObject("report").put("nodes", graph.nodes().size()).put("directedEdges", graph.edges().size());
        var source = manifest.putArray("sources").addObject().put("id", "fixture").put("url", "https://example.invalid/synthetic-test-fixture")
                .put("licenseUrl", "https://example.invalid/test-only-license").put("license", "SYNTHETIC_TEST_ONLY")
                .put("version", "TEST").put("acquiredAt", "2026-01-01T00:00:00Z").put("unit", "degrees")
                .put("coverage", "SYNTHETIC").put("sha256", "0".repeat(64)).put("status", "SOURCE_DATA");
        source.putArray("limitations").add("SYNTHETIC TEST ONLY, never production");
        if (variant.startsWith("regional")) {
            manifest.put("schemaVersion", 2).put("validationProfile", "GLOBAL_REGIONAL_RESEARCH_V1");
            var control = manifest.putObject("regionalControls").put("policy", "SOURCE_COVERAGE_PRIORITY_V1")
                    .put("regionId", "NOAA_NEW_YORK_V2").put("chartSourceId", "fixture")
                    .put("evidenceTable", "regional-evidence.jsonl");
            for (String field : List.of("chartSourceSha256", "regionalGraphVersion", "regionalArtifactSha256",
                    "globalArtifactSha256", "globalGraphVersion", "globalValidationSha256", "builderSha256", "policySha256"))
                control.put(field, "0".repeat(64));
            ((ObjectNode) manifest.path("report")).putObject("integrationCounts").put("regionalControlledEdges", graph.edges().size());
            var evidence = mapper.createObjectNode().put("id", "SYNTHETIC_CHART_FEATURE");
            evidence.putArray("sourceIds").add("fixture");
            evidence.putObject("geometry").put("type", "Point").putArray("coordinates").add(0).add(0);
            tables.put("regional-evidence.jsonl", (mapper.writeValueAsString(evidence) + "\n").getBytes(StandardCharsets.UTF_8));
            if (variant.equals("regional-downgrade")) manifest.put("schemaVersion", 1);
            if (variant.equals("regional-wrong-source")) control.put("chartSourceSha256", "1".repeat(64));
            if (variant.equals("regional-missing-control")) manifest.remove("regionalControls");
        }
        var hashes = manifest.putObject("tables");
        for (var entry : tables.entrySet()) hashes.put(entry.getKey(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entry.getValue())));
        if (variant.equals("regional-missing-evidence")) tables.remove("regional-evidence.jsonl");
        if (variant.equals("regional-corrupt-evidence")) tables.put("regional-evidence.jsonl", "{}\n".getBytes(StandardCharsets.UTF_8));
        if (variant.equals("missing-license")) source.remove("license");
        if (variant.equals("unknown-source")) source.put("id", "different-fixture");
        if (variant.equals("tampered")) tables.put("nodes.jsonl", (nodeLines + "\n").getBytes(StandardCharsets.UTF_8));
        if (variant.equals("zero-step")) ((ObjectNode) manifest.path("parameters")).put("geodesic_step_m", 0);
        Path file = directory.resolve(variant + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            for (var entry : tables.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write(mapper.writeValueAsBytes(manifest)); zip.closeEntry();
        }
        return file;
    }

    @Test void acceptsConsistentSyntheticArchiveAndChecksPhysicalLengths() throws Exception {
        var graph = new GraphArtifactLoader(1000000).load(artifact("valid"));
        assertThat(graph.nodes()).hasSize(5); assertThat(graph.edges()).hasSize(6);
        assertThatThrownBy(() -> graph.outgoing(0).clear()).isInstanceOf(UnsupportedOperationException.class);
        graph.manifest().deepCopy();
    }
    @Test void missingProvenanceUnknownSourcesInvalidGeometryAndCorruptionFailClosed() throws Exception {
        for (String variant : List.of("missing-license", "unknown-source", "tampered", "zero-step")) {
            var file = artifact(variant);
            assertThatThrownBy(() -> new GraphArtifactLoader(1000000).load(file)).isInstanceOf(java.io.IOException.class);
        }
    }
    @Test void configuredArtifactNeedsOperatorPinnedDigest() {
        assertThat(new GraphCatalog("not-read-without-digest.zip", 1000000).status()).isEqualTo("ARTIFACT_SHA256_NOT_CONFIGURED");
    }
    @Test void regionalSourceAndEvidenceCannotBeMissingChangedOrDowngraded() throws Exception {
        assertThat(new GraphArtifactLoader(1000000).load(artifact("regional-valid")).edges())
                .allSatisfy(edge -> assertThat(edge.regionalControlIds()).containsExactly("NOAA_NEW_YORK_V2"));
        for (String variant : List.of("regional-missing-evidence", "regional-corrupt-evidence", "regional-downgrade",
                "regional-wrong-source", "regional-missing-control")) {
            var file = artifact(variant);
            assertThatThrownBy(() -> new GraphArtifactLoader(1000000).load(file)).as(variant).isInstanceOf(java.io.IOException.class);
        }
    }
    @Test void catalogRequiresMatchingRegionalReportEvenWhenGlobalChecksPass() throws Exception {
        Path file = artifact("regional-valid");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file)));
        var report = mapper.createObjectNode().put("geometricCheck", "PASS").put("reciprocalGeometryVerified", true)
                .put("graphVersion", "a".repeat(64)).put("checkedReciprocalPairs", 3).put("coveredPortCount", 0);
        Path reportFile = directory.resolve("global-only-report.json");
        mapper.writeValue(reportFile.toFile(), report);
        assertThat(new GraphCatalog(file.toString(), 1000000, digest, reportFile.toString()).status())
                .isEqualTo("ARTIFACT_INVALID_OR_INACCESSIBLE");
    }
}
