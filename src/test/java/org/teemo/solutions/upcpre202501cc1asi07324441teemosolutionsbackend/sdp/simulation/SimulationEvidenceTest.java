package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.ScenarioSimulationTest.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.SimulationModel.*;

class SimulationEvidenceTest {
    @Test void replayExampleAndMaximumBudgetsPreservePriorBindings() throws Exception {
        Map<String, String> bindings = new TreeMap<>();
        Path prior = Path.of("data/maritime-routing/phase5-evidence-v1");
        var frozen = JSON.readTree(Files.readAllBytes(prior.resolve("bindings.json"))).path("implementation");
        var fields = frozen.fields(); int preserved = 0;
        while (fields.hasNext()) {
            var field = fields.next();
            assertThat(sha(Path.of(field.getKey()))).as("Frozen %s", field.getKey()).isEqualTo(field.getValue().asText());
            bindings.put(field.getKey(), field.getValue().asText()); preserved++;
        }
        var status = JSON.readTree(Files.readAllBytes(prior.resolve("status.json")));
        assertThat(status.path("status").asText()).isEqualTo("PASS");
        var evidence = status.path("evidenceFiles").fields();
        while (evidence.hasNext()) {
            var file = evidence.next(); assertThat(sha(prior.resolve(file.getKey()))).isEqualTo(file.getValue().asText());
        }
        bindings.put(prior.resolve("status.json").toString().replace('\\', '/'), sha(prior.resolve("status.json")));
        String base = "org/teemo/solutions/upcpre202501cc1asi07324441teemosolutionsbackend/sdp/simulation";
        for (String root : List.of("src/main/java/", "src/test/java/")) try (var paths = Files.list(Path.of(root + base))) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".java")).toList()) bindings.put(p.toString().replace('\\', '/'), sha(p));
        }
        for (String file : List.of("docs/maritime-routing/phase6-example.json", "docs/maritime-routing/phase6-api.md",
                "docs/maritime-routing/phase6-plan.md", "scripts/maritime/create_phase6_example.py")) bindings.put(file, sha(Path.of(file)));
        var mvc = MockMvcBuilders.standaloneSetup(new SimulationController(ENGINE)).build();
        String input = Files.readString(Path.of("docs/maritime-routing/phase6-example.json"));
        long start = System.nanoTime();
        String first = mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        double exampleMs = (System.nanoTime() - start) / 1e6;
        String replay = mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        var result = JSON.readTree(first);
        assertThat(result.path("evidenceStatus").asText()).isEqualTo("USER_DECLARED_UNCALIBRATED");
        assertThat(result.path("paretoAlternativeIds").toString()).isEqualTo("[\"economy\",\"protected\"]");

        var original = example(); List<Alternative> alternatives = new ArrayList<>();
        for (int i = 0; i < 16; i++) alternatives.add(new Alternative("copy-" + i, original.alternatives().get(0).contract()));
        List<JointScenario> rows = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            Map<String, Outcome> outcomes = new TreeMap<>();
            for (var a : alternatives) outcomes.put(a.id(), new Outcome(i % 2 == 0 ? "normal" : "storm", new BigDecimal(i % 2 == 0 ? "48" : "120")));
            rows.add(new JointScenario("joint-" + i, new BigDecimal("0.00390625"), outcomes));
        }
        var distribution = new Distribution("SYNTHETIC-CAPACITY-001", "Generated engineering capacity fixture; not observations",
                "All alternatives share each joint row", "Same synthetic cargo and identical contracts", rows);
        var maximum = new Request(ScenarioSimulation.VERSION, true, 7L, 200000, new BigDecimal(".9999"), new BigDecimal(".9999"),
                alternatives, distribution, original.objectives());
        start = System.nanoTime();
        var maximumResult = ENGINE.evaluate(maximum); double maximumMs = (System.nanoTime() - start) / 1e6;
        assertThat(maximumResult.paretoAlternativeIds()).hasSize(16);
        assertThat(maximumResult.sampledScenarioCounts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(200000);
        assertThat(maximumResult.alternatives()).hasSize(16);
        assertThat(maximumResult.sampledScenarioCounts()).hasSize(256);
        Path out = Path.of(System.getProperty("maritime.simulation.report", "target/maritime-routing/phase6")); Files.createDirectories(out);
        Files.writeString(out.resolve("example-response.json"), first);
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("capacity-request.json").toFile(), maximum);
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("runtime.json").toFile(), Map.of(
                "exampleHttpMilliseconds", exampleMs, "identicalHttpReplay", true, "capacityMilliseconds", maximumMs,
                "capacitySamples", 200000, "capacityAlternatives", 16, "capacityScenarios", 256,
                "capacityFrontierSize", maximumResult.paretoAlternativeIds().size(),
                "heapUsedAfterQueries", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                "heapCap", Runtime.getRuntime().maxMemory(), "capacityInputSha256", maximumResult.inputSha256()));
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("bindings.json").toFile(), Map.of(
                "scope", "PHASE6_DECLARED_JOINT_SCENARIO_RESEARCH", "modelVersion", ScenarioSimulation.VERSION,
                "preservedPhase5Bindings", preserved, "implementation", bindings,
                "responseSha256", sha(out.resolve("example-response.json")),
                "runtimeSha256", sha(out.resolve("runtime.json")), "capacityRequestSha256", sha(out.resolve("capacity-request.json"))));
    }
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
