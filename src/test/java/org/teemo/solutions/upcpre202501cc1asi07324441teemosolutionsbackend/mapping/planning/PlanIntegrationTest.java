package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractEvaluator;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.ScenarioSimulation;

class PlanIntegrationTest {
    @TempDir Path directory;
    static final ObjectMapper JSON = PlanJson.MAPPER;
    static PlanModel.Request example() throws Exception {
        return JSON.readValue(Files.readString(Path.of("docs/maritime-routing/phase7-example.json")), PlanModel.Request.class);
    }
    static PlanModel.Request change(java.util.function.Consumer<JsonNode> action) throws Exception {
        JsonNode tree = JSON.valueToTree(example()); action.accept(tree); return JSON.treeToValue(tree, PlanModel.Request.class);
    }
    PlanWeatherGateway gateway() throws Exception {
        var gateway = mock(PlanWeatherGateway.class); var first = example().routes().get(0).request();
        when(gateway.provenance()).thenReturn(JSON.createObjectNode().put("graphVersion", first.expectedGraphVersion()).put("forecastVersion", first.expectedForecastVersion()));
        when(gateway.route(any())).thenAnswer(call -> {
            var r = (org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather.WeatherRoutingController.Request)call.getArgument(0);
            int hours = r.vessel().calmSpeedKnots() == 14 ? 16 : r.closedEdgeIds() != null && !r.closedEdgeIds().isEmpty() ? 14 : 12;
            var response = JSON.createObjectNode().put("status", "FOUND_RESEARCH_SCENARIO").put("graphVersion", r.expectedGraphVersion())
                    .put("forecastVersion", r.expectedForecastVersion()).put("forecastValidUntil", r.departure().plusSeconds(168*3600).toString());
            response.putObject("route").put("arrival", r.departure().plusSeconds(hours*3600L).toString()).put("distanceM", 4.0e7)
                    .put("sailingSeconds", hours*3600-60).put("modelledWaitingSeconds", 60);
            return response;
        }); return gateway;
    }
    PlanService service(PlanWeatherGateway gateway) {
        var contracts = new ContractEvaluator(); return new PlanService(gateway, contracts, new ScenarioSimulation(contracts), new PlanStore(directory.toString()));
    }
    @Test void routesFeedDurationsAndEveryActorBindingWhileContentSurvivesReload() throws Exception {
        var service = service(gateway()); var saved = service.create(example()); String id = saved.path("planId").asText();
        assertThat(service.get(id)).isEqualTo(saved);
        var read = new PlanStore(directory.toString()).get(id);
        assertThat(read.path("contentSha256")).isEqualTo(saved.path("contentSha256"));
        var content = saved.path("content"); assertThat(content.path("bindings").size()).isEqualTo(3);
        var alternatives = content.path("comparison").path("alternatives");
        var economy = java.util.stream.StreamSupport.stream(alternatives.spliterator(), false).filter(a -> a.path("id").asText().equals("economy")).findFirst().orElseThrow();
        // Twelve physical hours + two non-sailing hours + E[additional] = 17.36.
        assertThat(economy.path("metrics").path("DURATION_HOURS").path("finiteDistributionReference").path("mean").decimalValue())
                .isEqualByComparingTo("17.36");
        assertThat(economy.path("metrics").path("BUYER_TOTAL_COST").path("finiteDistributionReference").path("mean").decimalValue())
                .isEqualByComparingTo("1520");
        assertThat(content.path("bindings").get(1).path("cargoRiskBearer").asText()).isEqualTo("BUYER");
        assertThat(content.path("bindings").get(1).path("routingControl").asText()).isEqualTo("CARRIER");
    }
    @Test void recalculationCreatesNewImmutableRevisionAndRecordsReview() throws Exception {
        var service = service(gateway()); var first = service.create(example());
        var replacement = change(j -> ((ObjectNode)j.path("routes").get(0).path("request")).putArray("closedEdgeIds").add(12));
        var review = new PlanModel.Recalculation(first.path("contentSha256").asText(), "Synthetic assumptions explicitly reviewed", replacement);
        var next = service.recalculate(first.path("planId").asText(), review);
        assertThat(next.path("planId")).isNotEqualTo(first.path("planId"));
        assertThat(service.get(first.path("planId").asText())).isEqualTo(first);
        assertThat(next.path("content").path("parent").path("contentSha256")).isEqualTo(first.path("contentSha256"));
        assertThat(next.path("content").path("changes").path("changedInputSections").toString()).isEqualTo("[\"routes\"]");
        assertThatThrownBy(() -> service.recalculate(first.path("planId").asText(), new PlanModel.Recalculation("wrong", "reviewed", replacement)))
                .hasMessage("PARENT_CONTENT_VERSION_MISMATCH");
    }
    @Test void onePhysicalFailurePreventsAnyComparisonSnapshot() throws Exception {
        var gateway = gateway(); doThrow(new PlanFailure(422, "PHYSICAL_ROUTE_REJECTED", Map.of("status", "SEARCH_BUDGET_EXHAUSTED"))).when(gateway).route(any());
        assertThatThrownBy(() -> service(gateway).create(example())).hasMessage("PHYSICAL_ROUTE_REJECTED");
        try (var files = Files.list(directory)) { assertThat(files.count()).isZero(); }
    }
    @Test void validatesContractMappingCommonVoyageBudgetsAndEconomicCompletenessBeforeSearch() throws Exception {
        List<java.util.function.Consumer<JsonNode>> edits = List.of(
                j -> ((ObjectNode)j).put("acknowledgeDeclaredEventMapping", false),
                j -> ((ObjectNode)j.path("alternatives").get(0)).put("routeId", "unknown"),
                j -> ((ObjectNode)j.path("alternatives").get(0)).put("carriageLegId", "unknown"),
                j -> ((ObjectNode)j.path("alternatives").get(0)).putNull("nonSailingHours"),
                j -> ((ObjectNode)j.path("routes").get(1).path("request")).put("originNodeId", "other"),
                j -> ((ObjectNode)j.path("routes").get(0).path("request").path("search")).put("maximumRuntimeSeconds", 120),
                j -> ((ObjectNode)j.path("routes").get(0).path("request")).putArray("closedEdgeIds").addNull(),
                j -> ((ObjectNode)j.path("routes").get(0).path("request")).putArray("prohibitedZones").addNull(),
                j -> ((ObjectNode)j.path("alternatives").get(0).path("contract").path("fees").get(0)).putNull("amount"),
                j -> ((ObjectNode)j.path("simulation").path("distribution").path("scenarios").get(0).path("outcomes").path("economy")).put("additionalHours", -1));
        for (var edit : edits) {
            var gateway = gateway(); var bad = change(edit);
            assertThatThrownBy(() -> service(gateway).create(bad)).isInstanceOf(IllegalArgumentException.class);
            verify(gateway, never()).route(any());
        }
    }
    @Test void hashCanonicalizesNumbersAndStoreRejectsCorruptionAndUnknownIds() throws Exception {
        assertThat(PlanJson.sha(JSON.readTree("{\"a\":1.0e7,\"b\":2}"))).isEqualTo(PlanJson.sha(JSON.readTree("{\"b\":2.00,\"a\":10000000}")));
        var store = new PlanStore(directory.toString()); var saved = store.save(JSON.readTree("{\"x\":1.0e7}"));
        String id = saved.path("planId").asText();
        assertThat(store.get(id).path("contentSha256")).isEqualTo(saved.path("contentSha256"));
        Files.writeString(directory.resolve(id+".json"), "null");
        assertThatThrownBy(() -> store.get(id)).hasMessage("PLAN_INTEGRITY_FAILED");
        assertThatThrownBy(() -> store.get("../../secret")).hasMessage("PLAN_NOT_FOUND");
        assertThatThrownBy(() -> new PlanStore("").available()).hasMessage("PLAN_STORE_NOT_CONFIGURED");
    }
    @Test void quotaPreventsUnboundedStorage() throws Exception {
        for (int i = 0; i < 128; i++) Files.writeString(directory.resolve("fixture-"+i), "{}");
        assertThatThrownBy(() -> new PlanStore(directory.toString()).save(JSON.createObjectNode())).hasMessage("PLAN_STORE_QUOTA_REACHED");
    }
    @Test void httpProvidesDetailsStrictSchemaAndVersionErrors() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PlanController(service(gateway())))
                .addFilters(new org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.UnverifiedLegacyRoutingFilter(JSON)).build();
        mvc.perform(post("/api/incoterms/calculate").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("UNVERIFIED_LEGACY_MODEL_DISABLED"));
        String json = Files.readString(Path.of("docs/maritime-routing/phase7-example.json"));
        var response = mvc.perform(post("/api/v2/maritime/plans").contentType("application/json").content(json)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        String id = JSON.readTree(response.getContentAsByteArray()).path("planId").asText();
        mvc.perform(get("/api/v2/maritime/plans/"+id+"/provenance")).andExpect(status().isOk()).andExpect(jsonPath("$.bindings.length()").value(3));
        mvc.perform(get("/api/v2/maritime/plans/"+id+"/explanation")).andExpect(status().isOk()).andExpect(jsonPath("$.missingData.length()").value(5));
        mvc.perform(post("/api/v2/maritime/plans").contentType("application/json").content(json.replace(PlanService.VERSION, "old"))).andExpect(status().isConflict());
        for (String invalid : new String[] {"null", "{}", json+"{}", json.replace("\"seed\": 20260922", "\"seed\": 2.5"),
                json.replace("\"nonSailingHours\": 2", "\"nonSailingHours\": 2, \"durationHours\": 1"),
                json.replace("\"samples\": 20000", "\"samples\": 20000, \"samples\": 100")})
            mvc.perform(post("/api/v2/maritime/plans").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
    }
    @Test void buildPermitIsReleasedAndRejectsConcurrentMutation() throws Exception {
        var service = mock(PlanService.class); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(service.create(any())).thenAnswer(call -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(); return JSON.createObjectNode(); });
        var controller = new PlanController(service); var pool = Executors.newSingleThreadExecutor();
        String json = Files.readString(Path.of("docs/maritime-routing/phase7-example.json"));
        try {
            var first = pool.submit(() -> controller.create(json)); assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(controller.create(json).getStatusCode().value()).isEqualTo(429);
            release.countDown(); assertThat(first.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.create("invalid").getStatusCode().value()).isEqualTo(400);
            assertThat(controller.create(json).getStatusCode().value()).isEqualTo(200);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
