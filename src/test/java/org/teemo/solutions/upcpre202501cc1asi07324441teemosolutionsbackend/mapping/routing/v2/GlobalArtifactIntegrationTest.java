package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Requires a real, locally acquired artifact; never substitutes test fixtures for global evidence. */
@EnabledIfSystemProperty(named = "maritime.graph", matches = ".+")
class GlobalArtifactIntegrationTest {
    @Test void realSourcePortPairsAndRuntimeBenchmark() throws Exception {
        long started = System.nanoTime();
        Path artifact = Path.of(System.getProperty("maritime.graph"));
        long maxEntryBytes = Long.parseLong(System.getProperty("maritime.max-entry-bytes", "536870912"));
        String digest;
        try (var stream = Files.newInputStream(artifact)) {
            var sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int read;
            while ((read = stream.read(buffer)) != -1) sha.update(buffer, 0, read);
            digest = HexFormat.of().formatHex(sha.digest());
        }
        Path validation = Path.of(System.getProperty("maritime.validation", artifact.resolveSibling(
                artifact.getFileName().toString().replaceFirst("\\.zip$", ".validation.json")).toString()));
        GraphCatalog catalog = new GraphCatalog(artifact.toString(), maxEntryBytes, digest, validation.toString());
        if (catalog.current().isEmpty()) {
            // Local test diagnostic only; production catalog never exposes parser/path details.
            new GraphArtifactLoader(maxEntryBytes).load(artifact);
        }
        PhysicalGraph graph = catalog.current().orElseThrow(() -> new AssertionError(catalog.status()));
        assertThat(catalog.current()).containsSame(graph);
        double loadMs = (System.nanoTime() - started) / 1e6;
        List<PhysicalGraph.Port> selected = new ArrayList<>();
        // IDs and coordinates come exclusively from the actual source artifact.
        for (String country : List.of("JP", "NZ", "CL", "ZA", "GB", "US", "AU", "BR")) {
            graph.ports().values().stream().filter(p -> p.id().startsWith("UNLOCODE:" + country)
                    && "CONNECTED_REFERENCE_POINT".equals(p.status())).findFirst().ifPresent(selected::add);
        }
        assertThat(selected).hasSize(8);
        String protocolPath = System.getProperty("maritime.protocol");
        com.fasterxml.jackson.databind.JsonNode protocol = protocolPath == null ? null : new ObjectMapper().readTree(Path.of(protocolPath).toFile());
        if (graph.manifest().path("schemaVersion").asInt() == 2) {
            assertThat(protocol).as("Combined artifact requires a registered evaluation protocol").isNotNull();
            assertThat(protocol.path("scope").asText()).isEqualTo("PHASE3_GLOBAL_RESEARCH_V2");
            assertThat(graph.manifest().path("report").path("regionalNodesInLargestPhysicalComponent").asInt()).isPositive();
            assertThat(graph.edges().stream().filter(e -> !e.regionalControlIds().isEmpty()).count()).isPositive();
            var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false));
            assertThat(graph.edges().stream().filter(e -> !e.regionalControlIds().isEmpty() && !e.restrictions().isEmpty()).toList())
                    .isNotEmpty().allSatisfy(e -> assertThat(policy.evaluate(e).allowed()).isFalse());
        }
        List<List<PhysicalGraph.Port>> pairs = new ArrayList<>();
        if (protocol != null) {
            for (var pair : protocol.path("cases")) {
                var a = graph.ports().get(pair.path("originId").asText());
                var b = graph.ports().get(pair.path("destinationId").asText());
                assertThat(a).isNotNull(); assertThat(b).isNotNull();
                assertThat(a.status()).isEqualTo("CONNECTED_REFERENCE_POINT");
                assertThat(b.status()).isEqualTo("CONNECTED_REFERENCE_POINT");
                pairs.add(List.of(a, b));
            }
            assertThat(pairs).hasSize(9);
        } else for (int i = 0; i < selected.size(); i++) pairs.add(List.of(selected.get(i), selected.get((i + 1) % selected.size())));
        List<Map<String, Object>> cases = new ArrayList<>();
        Set<String> basins = new HashSet<>();
        for (var pair : pairs) {
            var a = pair.get(0); var b = pair.get(1);
            var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false));
            long time = System.nanoTime();
            var result = new RouteSearch(graph, policy, 2000000).alternatives(a.nodeId(), b.nodeId(), 1, 1, 0);
            double elapsed = (System.nanoTime() - time) / 1e6;
            assertThat(result.budgetExhausted()).isFalse(); assertThat(result.paths()).hasSize(1);
            var route = result.paths().get(0);
            assertThat(route.nodes()).doesNotHaveDuplicates();
            assertThat(route.distanceM()).isGreaterThanOrEqualTo(a.point().distanceTo(b.point()) - .01);
            boolean crossesDateline = false, crossesEquator = false;
            for (var edge : route.edges()) {
                for (var point : edge.geometry()) {
                    double lon = point.longitude(), lat = point.latitude();
                    if (lat >= -45 && lat <= 25 && lon >= 30 && lon <= 110) basins.add("INDIAN");
                    if (lat >= -45 && lat <= 45 && lon >= -50 && lon <= 0) basins.add("ATLANTIC");
                    if (lat >= -50 && lat <= 50 && (lon >= 130 || lon <= -100)) basins.add("PACIFIC");
                }
                for (int p = 1; p < edge.geometry().size(); p++) {
                    var previous = edge.geometry().get(p - 1); var current = edge.geometry().get(p);
                    crossesDateline |= Math.abs(previous.longitude() - current.longitude()) > 180;
                    crossesEquator |= (previous.latitude() < 0 && current.latitude() >= 0)
                            || (previous.latitude() >= 0 && current.latitude() < 0);
                }
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("originId", a.id()); row.put("originName", a.name());
            row.put("destinationId", b.id()); row.put("destinationName", b.name());
            row.put("originPoint", a.point()); row.put("destinationPoint", b.point());
            row.put("distanceM", route.distanceM()); row.put("geodesicLowerBoundM", a.point().distanceTo(b.point()));
            row.put("milliseconds", elapsed); row.put("expandedNodes", result.expandedNodes());
            row.put("edges", route.edges().size()); row.put("crossesAntimeridian", crossesDateline);
            row.put("crossesEquator", crossesEquator);
            cases.add(row);
        }
        assertThat(cases).as("Real source routes cross the antimeridian")
                .anyMatch(c -> Boolean.TRUE.equals(c.get("crossesAntimeridian")));
        assertThat(cases).as("Real source routes cross hemispheres")
                .anyMatch(c -> Boolean.TRUE.equals(c.get("crossesEquator")));
        if (protocol != null) assertThat(basins).contains("INDIAN", "ATLANTIC", "PACIFIC");
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("graphVersion", graph.version()); report.put("loadMilliseconds", loadMs);
        report.put("artifactSha256", digest); report.put("catalogStatus", catalog.status());
        report.put("maxEntryBytes", maxEntryBytes); report.put("measuredAt", java.time.Instant.now().toString());
        report.put("distanceLowerBoundFactor", graph.distanceLowerBoundFactor());
        report.put("nodes", graph.nodes().size()); report.put("directedEdges", graph.edges().size());
        report.put("heapUsedBytesAfterQueries", runtime.totalMemory() - runtime.freeMemory());
        report.put("heapMaxBytes", runtime.maxMemory()); report.put("javaVersion", System.getProperty("java.version"));
        report.put("cases", cases); report.put("limitations", "Single process benchmark, not a load/SLA test; no AIS or operational validation");
        report.put("basinBoundingBoxChecks", basins);
        if (protocol != null) {
            report.put("protocolSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(protocolPath)))));
            report.put("regionalHardRestrictionsCheck", "PASS");
            report.put("regionalNodesInLargestPhysicalComponent", graph.manifest().path("report").path("regionalNodesInLargestPhysicalComponent"));
        }
        var a = selected.get(4); var b = selected.get(5);
        long alternativesStarted = System.nanoTime();
        var diverse = new RouteSearch(graph, RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false)), 2000000)
                .alternatives(a.nodeId(), b.nodeId(), 3, 20, 50000);
        assertThat(diverse.paths().size()).as("Geographically distinct global research alternatives").isGreaterThanOrEqualTo(2);
        assertThat(diverse.budgetExhausted()).isFalse();
        report.put("alternatives", Map.of("originId", a.id(), "destinationId", b.id(), "requestedK", 3,
                "returnedK", diverse.paths().size(), "minimumSeparationM", 50000,
                "distancesM", diverse.paths().stream().map(RouteSearch.Path::distanceM).toList(),
                "milliseconds", (System.nanoTime() - alternativesStarted) / 1e6, "budgetExhausted", diverse.budgetExhausted()));
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(catalog)).build();
        mvc.perform(get("/api/v2/maritime/graph")).andExpect(status().isOk())
                .andExpect(jsonPath("$.graphVersion").value(graph.version()));
        var request = new LinkedHashMap<String, Object>();
        request.put("originPortId", a.id()); request.put("destinationPortId", b.id());
        request.put("acknowledgeResearchLimitations", true); request.put("k", 3);
        mvc.perform(post("/api/v2/maritime/routes").contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.routes.length()").value(3))
                .andExpect(jsonPath("$.graphVersion").value(graph.version()))
                .andExpect(jsonPath("$.routes[0].estimatedHours").isEmpty())
                .andExpect(jsonPath("$.routes[0].coverage.purpose").value("RESEARCH_NOT_NAVIGATION"))
                .andExpect(jsonPath("$.routes[0].coverage.graphVersion").value(graph.version()))
                .andExpect(jsonPath("$.inputProvenance.source").value("USER_SUPPLIED"));
        request.put("requireKnownLegalStatus", true);
        mvc.perform(post("/api/v2/maritime/routes").contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rejectedEdgeEvaluations.LEGAL_STATUS_UNKNOWN").exists());
        report.put("realArtifactHttpChecks", "PASS: manifest, three routes, missing time, unknown legal permission rejected");
        if (protocol != null) {
            request.put("requireKnownLegalStatus", false);
            request.put("userSuppliedDraftM", 1);
            request.put("userSuppliedClearanceM", 0);
            mvc.perform(post("/api/v2/maritime/routes").contentType("application/json")
                            .content(new ObjectMapper().writeValueAsBytes(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.rejectedEdgeEvaluations.DEPTH_UNAVAILABLE").exists());
            report.put("unknownDepthHttpCheck", "PASS");
        }
        Path reportDirectory = Path.of(System.getProperty("maritime.report-directory", "target/maritime-routing"));
        Files.createDirectories(reportDirectory);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportDirectory.resolve("runtime-benchmark.json").toFile(), report);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                reportDirectory.resolve("runtime-benchmark-" + graph.version() + ".json").toFile(), report);
        System.out.println("MARITIME_BENCHMARK " + new ObjectMapper().writeValueAsString(report));
    }
}
