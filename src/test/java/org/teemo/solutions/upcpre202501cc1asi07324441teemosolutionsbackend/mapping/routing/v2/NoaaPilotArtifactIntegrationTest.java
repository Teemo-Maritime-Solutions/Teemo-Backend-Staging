package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Negative real-source evidence: this pinned regional pilot must not become an eligible route. */
@EnabledIfSystemProperty(named = "maritime.enc-pilot", matches = ".+")
class NoaaPilotArtifactIntegrationTest {
    @Test void realNoaaEvidenceLoadsButUnresolvedRestrictionsAndActivationRemainBlocked() throws Exception {
        long started = System.nanoTime();
        Path artifact = Path.of(System.getProperty("maritime.enc-pilot"));
        Path validationPath = Path.of(System.getProperty("maritime.enc-validation"));
        var mapper = new ObjectMapper();
        var validation = mapper.readTree(validationPath.toFile());
        var graph = new GraphArtifactLoader(200_000_000).load(artifact);
        double loadMs = (System.nanoTime() - started) / 1e6;
        String digest;
        try (var input = Files.newInputStream(artifact)) {
            var hash = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
            digest = HexFormat.of().formatHex(hash.digest());
        }
        assertThat(validation.path("artifactSha256").asText()).isEqualTo(digest);
        assertThat(validation.path("graphVersion").asText()).isEqualTo(graph.version());
        assertThat(validation.path("geometricCheck").asText()).isEqualTo("PASS");
        assertThat(validation.path("validationGate").asText()).isEqualTo("NOT_PASSED");
        assertThat(validation.path("checkedReciprocalPairs").asLong() * 2).isEqualTo(graph.edges().size());
        String expectedProfile = System.getProperty("maritime.enc-profile", "NOAA_ENC_REGIONAL_V1");
        assertThat(expectedProfile).isIn("NOAA_ENC_REGIONAL_V1", "NOAA_ENC_REGIONAL_V2");
        assertThat(graph.manifest().path("validationProfile").asText()).isEqualTo(expectedProfile);
        if ("NOAA_ENC_REGIONAL_V2".equals(expectedProfile)) {
            assertThat(graph.edges()).as("Source bridge clearance remains an unresolved restriction")
                    .anyMatch(e -> e.restrictions().stream().anyMatch(r -> r.startsWith("noaa-enc:141:")));
            assertThat(graph.ports()).hasSize(19);
        }
        assertThat(graph.ports()).isNotEmpty();
        assertThat(graph.ports().values()).allMatch(p -> p.nodeId() == null && !"CONNECTED_REFERENCE_POINT".equals(p.status()));
        var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false));
        assertThat(graph.edges()).isNotEmpty().allSatisfy(edge -> {
            assertThat(edge.minimumDepthM()).isNull();
            assertThat(edge.legalStatus()).isEqualTo("UNKNOWN");
            assertThat(edge.restrictions()).isNotEmpty();
            assertThat(policy.evaluate(edge).allowed()).isFalse();
            assertThat(policy.evaluate(edge).reason()).isEqualTo("UNRESOLVED_HARD_RESTRICTION");
        });
        // Even adjacent source-derived mesh nodes cannot be joined by routing policy.
        var edge = graph.edges().get(0);
        var result = new RouteSearch(graph, policy, 10000).alternatives(edge.from(), edge.to(), 3, 10, 0);
        assertThat(result.paths()).isEmpty();
        assertThat(result.budgetExhausted()).isFalse();
        assertThat(result.rejectedEvaluations()).containsKey("UNRESOLVED_HARD_RESTRICTION");
        var catalog = new GraphCatalog(artifact.toString(), 200_000_000, digest, validationPath.toString());
        assertThat(catalog.current()).isEmpty();
        assertThat(catalog.status()).isEqualTo("ARTIFACT_INVALID_OR_INACCESSIBLE");
        var report = new LinkedHashMap<String, Object>();
        report.put("graphVersion", graph.version()); report.put("artifactSha256", digest);
        report.put("validationProfile", expectedProfile);
        report.put("measuredAt", java.time.Instant.now().toString()); report.put("loadMilliseconds", loadMs);
        report.put("nodes", graph.nodes().size()); report.put("directedEdges", graph.edges().size());
        report.put("sourceBerths", graph.ports().size()); report.put("returnedRoutes", result.paths().size());
        report.put("rejectedEvaluations", result.rejectedEvaluations()); report.put("catalogStatus", catalog.status());
        report.put("heapUsedBytesAfterChecks", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        report.put("heapMaxBytes", Runtime.getRuntime().maxMemory());
        report.put("limitation", "Negative regional evidence; not a successful berth route, global validation, peak RSS or SLA");
        Path reportDirectory = Path.of(System.getProperty("maritime.report-directory", "target/maritime-routing"));
        Files.createDirectories(reportDirectory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportDirectory.resolve("noaa-pilot-" + graph.version() + ".json").toFile(), report);
        System.out.println("MARITIME_BENCHMARK " + mapper.writeValueAsString(report));
    }
}
