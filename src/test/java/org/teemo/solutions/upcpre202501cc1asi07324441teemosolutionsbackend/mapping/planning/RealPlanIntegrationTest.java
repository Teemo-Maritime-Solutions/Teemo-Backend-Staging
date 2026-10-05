package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractEvaluator;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.ScenarioSimulation;

@EnabledIfSystemProperty(named="maritime.plan.real", matches="true")
class RealPlanIntegrationTest {
    @TempDir Path directory;
    final ObjectMapper json = PlanJson.MAPPER;
    @Test void realPhysicalWeatherContractsComparisonAndClosureRecalculation() throws Exception {
        Path previousRoot = Path.of("data/maritime-routing/phase6-evidence-v1");
        var previousBindings = json.readTree(Files.readAllBytes(previousRoot.resolve("bindings.json")));
        Map<String, String> bindings = new TreeMap<>(); var fields = previousBindings.path("implementation").fields();
        while (fields.hasNext()) { var field = fields.next(); assertThat(ForecastSnapshot.sha256(Path.of(field.getKey()))).as(field.getKey()).isEqualTo(field.getValue().asText()); bindings.put(field.getKey(), field.getValue().asText()); }
        int priorCount = bindings.size();
        var previousStatus = json.readTree(Files.readAllBytes(previousRoot.resolve("status.json")));
        assertThat(previousStatus.path("status").asText()).isEqualTo("PASS");
        var files = previousStatus.path("evidenceFiles").fields();
        while (files.hasNext()) { var file = files.next(); assertThat(ForecastSnapshot.sha256(previousRoot.resolve(file.getKey()))).isEqualTo(file.getValue().asText()); }
        var protocol = json.readTree(Files.readAllBytes(Path.of("docs/maritime-routing/phase4-protocol-v3-20260922.json")));
        long start = System.nanoTime();
        var graphs = new GraphCatalog(protocol.path("graph").asText(), 1000000000, protocol.path("graphSha256").asText(), protocol.path("validation").asText());
        var graph = graphs.current().orElseThrow(() -> new AssertionError(graphs.status()));
        double graphMs = (System.nanoTime()-start)/1e6; start = System.nanoTime();
        var forecasts = new ForecastCatalog(protocol.path("manifest").asText(), protocol.path("forecastSha256").asText());
        var forecast = forecasts.current().orElseThrow(() -> new AssertionError(forecasts.status()));
        double forecastMs = (System.nanoTime()-start)/1e6;
        var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false));
        long blocked = graph.edges().stream().filter(e -> !e.regionalControlIds().isEmpty() && !e.restrictions().isEmpty())
                .peek(e -> assertThat(policy.evaluate(e).allowed()).isFalse()).count(); assertThat(blocked).isEqualTo(16330);
        var gateway = new PlanWeatherGateway(new WeatherRoutingController(graphs, forecasts), graphs, forecasts);
        var contracts = new ContractEvaluator(); var store = new PlanStore(directory.toString());
        var service = new PlanService(gateway, contracts, new ScenarioSimulation(contracts), store);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlanController(service)).build();
        var request = (ObjectNode)json.readTree(Files.readAllBytes(Path.of("docs/maritime-routing/phase7-example.json")));
        start = System.nanoTime();
        var first = call(mvc, "/api/v2/maritime/plans", request, 200); double planMs = (System.nanoTime()-start)/1e6;
        int closedEdge = -1;
        for (var leg : first.path("content").path("routes").path("base").path("route").path("legs"))
            if (!leg.path("edgeId").isNull()) { closedEdge = leg.path("edgeId").asInt(); break; }
        assertThat(closedEdge).isGreaterThanOrEqualTo(0);
        var replacement = request.deepCopy();
        for (var route : replacement.path("routes")) ((ObjectNode)route.path("request")).putArray("closedEdgeIds").add(closedEdge);
        var recalc = json.createObjectNode().put("expectedParentContentSha256", first.path("contentSha256").asText())
                .put("commercialReviewEvidence", "Synthetic research fees and probabilities explicitly retained after reviewing the edge closure; not repriced or recalibrated");
        recalc.set("replacement", replacement); start = System.nanoTime();
        var second = call(mvc, "/api/v2/maritime/plans/"+first.path("planId").asText()+"/recalculate", recalc, 200);
        double recalcMs = (System.nanoTime()-start)/1e6;
        assertThat(new PlanStore(directory.toString()).get(first.path("planId").asText())).isEqualTo(first);
        assertThat(second.path("content").path("parent").path("contentSha256")).isEqualTo(first.path("contentSha256"));
        for (var plan : List.of(first, second)) {
            var content = plan.path("content"); assertThat(content.path("bindings").size()).isEqualTo(3);
            assertThat(content.path("comparison").path("evidenceStatus").asText()).isEqualTo("USER_DECLARED_UNCALIBRATED");
            for (var route : content.path("routes")) {
                assertThat(route.path("graphVersion").asText()).isEqualTo(graph.version());
                assertThat(route.path("forecastVersion").asText()).isEqualTo(forecast.version());
                assertThat(route.path("fuelTonnes").isNull()).isTrue();
                for (var leg : route.path("route").path("legs")) if (!leg.path("edgeId").isNull()) {
                    int id = leg.path("edgeId").asInt(); assertThat(policy.evaluate(graph.edges().get(id)).allowed()).isTrue();
                    if (plan == second) assertThat(id).isNotEqualTo(closedEdge);
                }
            }
            for (var binding : content.path("bindings")) {
                String aid = binding.path("alternativeId").asText();
                for (var row : content.path("comparison").path("declaredInputs").path("distribution").path("scenarios")) {
                    var declared = java.util.stream.StreamSupport.stream(content.path("declaredInputs").path("simulation").path("distribution").path("scenarios").spliterator(), false)
                            .filter(r -> r.path("id").equals(row.path("id"))).findFirst().orElseThrow();
                    var expected = binding.path("physicalElapsedHours").decimalValue().add(binding.path("nonSailingHours").decimalValue())
                            .add(declared.path("outcomes").path(aid).path("additionalHours").decimalValue());
                    assertThat(row.path("outcomes").path(aid).path("durationHours").decimalValue()).isEqualByComparingTo(expected);
                }
            }
        }
        mvc.perform(get("/api/v2/maritime/plans/"+second.path("planId").asText()+"/provenance")).andExpect(status().isOk());
        mvc.perform(get("/api/v2/maritime/plans/"+second.path("planId").asText()+"/explanation")).andExpect(status().isOk());
        var badPin = request.deepCopy();
        for (var route : badPin.path("routes")) ((ObjectNode)route.path("request")).put("expectedForecastVersion", "wrong");
        call(mvc, "/api/v2/maritime/plans", badPin, 409);
        var sealed = request.deepCopy(); String originId = request.path("routes").get(0).path("request").path("originNodeId").asText();
        int origin = -1; for (int i=0; i<graph.nodes().size(); i++) if (graph.nodes().get(i).id().equals(originId)) { origin=i; break; }
        assertThat(origin).isGreaterThanOrEqualTo(0);
        for (var route : sealed.path("routes")) {
            var ids = ((ObjectNode)route.path("request")).putArray("closedEdgeIds"); graph.outgoing(origin).forEach(e -> ids.add(e.id()));
        }
        call(mvc, "/api/v2/maritime/plans", sealed, 422);
        try (var paths = Files.list(directory)) { assertThat(paths.count()).isEqualTo(2); }
        Path out = Path.of(System.getProperty("maritime.plan.report", "target/maritime-routing/phase7")); Files.createDirectories(out);
        Files.write(out.resolve("plan-v1.json"), PlanJson.bytes(first)); Files.write(out.resolve("plan-v2.json"), PlanJson.bytes(second));
        Files.write(out.resolve("recalculation-request.json"), PlanJson.bytes(recalc));
        Files.write(out.resolve("runtime.json"), PlanJson.bytes(Map.of("graphLoadMilliseconds", graphMs, "forecastLoadMilliseconds", forecastMs,
                "planMilliseconds", planMs, "recalculationMilliseconds", recalcMs, "closedEdgeId", closedEdge, "blockedRegionalEdges", blocked,
                "heapUsedBytesAfterQueries", Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(), "heapCap", Runtime.getRuntime().maxMemory(),
                "negativeHttpStatuses", List.of(409, 422), "storedRevisions", 2)));
        String base = "org/teemo/solutions/upcpre202501cc1asi07324441teemosolutionsbackend/mapping/planning";
        for (String root : List.of("src/main/java/", "src/test/java/")) try (var paths = Files.list(Path.of(root+base))) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".java")).toList()) bindings.put(p.toString().replace('\\', '/'), ForecastSnapshot.sha256(p));
        }
        for (String file : List.of("docs/maritime-routing/phase7-plan.md", "docs/maritime-routing/phase7-example.json", "scripts/maritime/create_phase7_example.py"))
            bindings.put(file, ForecastSnapshot.sha256(Path.of(file)));
        var outputs = new TreeMap<String, String>();
        for (String file : List.of("plan-v1.json", "plan-v2.json", "recalculation-request.json", "runtime.json")) outputs.put(file, ForecastSnapshot.sha256(out.resolve(file)));
        Files.write(out.resolve("bindings.json"), PlanJson.bytes(Map.of("scope", "PHASE7_INTEGRATED_RESEARCH_PLANS", "priorBoundFiles", priorCount,
                "implementation", bindings, "outputs", outputs)));
    }
    private JsonNode call(MockMvc mvc, String url, JsonNode request, int expectedStatus) throws Exception {
        var response = mvc.perform(post(url).contentType("application/json").content(PlanJson.bytes(request))).andReturn().getResponse();
        var body = json.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).as("%s %s", url, body.path("status").asText()+" "+body.path("details").toString()).isEqualTo(expectedStatus);
        return body;
    }
}
