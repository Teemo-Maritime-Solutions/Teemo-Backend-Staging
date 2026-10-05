package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in original forecast/graph evidence. Synthetic fixtures cannot satisfy this gate. */
@EnabledIfSystemProperty(named="maritime.weather.protocol", matches=".+")
class RealWeatherIntegrationTest {
    @Test void realForecastThreeBasinsCoverageAndApi() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path protocolPath = Path.of(System.getProperty("maritime.weather.protocol"));
        JsonNode protocol = mapper.readTree(protocolPath.toFile());
        for (var entries = protocol.path("implementation").fields(); entries.hasNext();) {
            var entry = entries.next(); assertThat(ForecastSnapshot.sha256(Path.of(entry.getKey()))).isEqualTo(entry.getValue().asText());
        }
        String phase3 = "docs/maritime-routing/phase3-research-protocol-v3-20260921.json";
        assertThat(ForecastSnapshot.sha256(Path.of(phase3))).isEqualTo(protocol.path("phase3ProtocolSha256").asText());
        for (var entries = mapper.readTree(Path.of(phase3).toFile()).path("implementation").fields(); entries.hasNext();) {
            var entry = entries.next(); assertThat(ForecastSnapshot.sha256(Path.of(entry.getKey()))).isEqualTo(entry.getValue().asText());
        }
        long start = System.nanoTime();
        var graphCatalog = new GraphCatalog(protocol.path("graph").asText(), 1_000_000_000,
                protocol.path("graphSha256").asText(), protocol.path("validation").asText());
        var graph = graphCatalog.current().orElseThrow(() -> new AssertionError(graphCatalog.status()));
        double graphLoadMs = (System.nanoTime()-start)/1e6; start = System.nanoTime();
        var weatherCatalog = new ForecastCatalog(protocol.path("manifest").asText(), protocol.path("forecastSha256").asText());
        var weather = weatherCatalog.current().orElseThrow(() -> new AssertionError(weatherCatalog.status()));
        double weatherLoadMs = (System.nanoTime()-start)/1e6;
        var vessel = mapper.treeToValue(protocol.path("vessel"), VesselModel.class);
        var options = mapper.treeToValue(protocol.path("search"), TemporalRouteSearch.Options.class);
        var constraints = new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false);
        var policy = RouteCostPolicy.distance(constraints);
        Instant departure = Instant.parse(protocol.path("departure").asText());
        List<Map<String,Object>> census = new ArrayList<>(); int connected = 0, firstAvailable = 0, allAvailable = 0;
        for (var port : graph.ports().values()) if ("CONNECTED_REFERENCE_POINT".equals(port.status())) {
            connected++; int valid = 0; boolean first = false;
            for (Instant at = weather.start(); !at.isAfter(weather.end()); at = at.plusSeconds(21600)) {
                try { weather.sample(port.point(), at); valid++; if (at.equals(departure)) first = true; }
                catch (WeatherField.Unavailable missing) { }
            }
            if (first) firstAvailable++; if (valid == weather.manifest().path("frames").size()) allAvailable++;
            census.add(Map.of("portId", port.id(), "validFrames", valid, "availableAtDeparture", first));
        }
        long blocked = graph.edges().stream().filter(e -> !e.regionalControlIds().isEmpty() && !e.restrictions().isEmpty())
                .peek(e -> assertThat(policy.evaluate(e).allowed()).isFalse()).count();
        // Includes the 16,178 original regional edges and 152 controlled connectors.
        assertThat(blocked).isEqualTo(16330);
        var mvc = MockMvcBuilders.standaloneSetup(new WeatherRoutingController(graphCatalog, weatherCatalog)).build();
        mvc.perform(get("/api/v2/maritime/weather")).andExpect(status().isOk())
                .andExpect(jsonPath("$.forecastVersion").value(weather.version()));
        List<Map<String,Object>> cases = new ArrayList<>(); ObjectNode firstRequest = null;
        for (var scenario : protocol.path("cases")) {
            int origin = nearest(graph, scenario.path("origin")), destination = nearest(graph, scenario.path("destination"));
            assertThat(origin).isNotEqualTo(destination);
            var baseline = new RouteSearch(graph, policy, 2000000).shortest(origin, destination, Set.of(), Set.of()).orElseThrow();
            assertThat(baseline).isNotNull();
            var request = mapper.createObjectNode().put("originNodeId", graph.nodes().get(origin).id())
                    .put("destinationNodeId", graph.nodes().get(destination).id()).put("departure", departure.toString())
                    .put("expectedGraphVersion", graph.version()).put("expectedForecastVersion", weather.version())
                    .put("mode", "HISTORICAL_RESEARCH_REPLAY").put("acknowledgeResearchLimitations", true)
                    .put("requireKnownLegalStatus", false);
            request.set("vessel", protocol.path("vessel")); request.set("search", protocol.path("search"));
            if (firstRequest == null) firstRequest = request.deepCopy();
            start = System.nanoTime();
            var response = mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json")
                    .content(mapper.writeValueAsBytes(request))).andReturn().getResponse();
            double elapsedMs = (System.nanoTime()-start)/1e6;
            var body = mapper.readTree(response.getContentAsByteArray());
            var row = new LinkedHashMap<String,Object>(); row.put("basin", scenario.path("basin").asText());
            row.put("request", request); row.put("httpStatus", response.getStatus()); row.put("response", body);
            row.put("originPoint", graph.nodes().get(origin).point()); row.put("destinationPoint", graph.nodes().get(destination).point());
            row.put("milliseconds", elapsedMs); row.put("distanceOnlyBaselineM", baseline.distanceM());
            row.put("calmConstantSpeedBaselineSeconds", baseline.distanceM()/(vessel.calmSpeedKnots()*VesselModel.KNOT_MPS));
            cases.add(row);
            System.out.println("PHASE4_CASE "+scenario.path("basin").asText()+" "+response.getStatus()+" "+elapsedMs+"ms "+body.path("status").asText());
        }
        Path output = Path.of(System.getProperty("maritime.weather.report", "target/maritime-routing/weather-20260922"));
        Files.createDirectories(output);
        var report = new LinkedHashMap<String,Object>();
        report.put("purpose", "REAL_FORECAST_UNCALIBRATED_RESEARCH_SCENARIOS"); report.put("protocolSha256", ForecastSnapshot.sha256(protocolPath));
        report.put("graphVersion", graph.version()); report.put("forecastVersion", weather.version());
        report.put("graphLoadMilliseconds", graphLoadMs); report.put("forecastLoadMilliseconds", weatherLoadMs);
        report.put("connectedPortReferences", connected); report.put("weatherAvailableAtDeparture", firstAvailable);
        report.put("weatherAvailableAllFrames", allAvailable); report.put("blockedRegionalEdges", blocked);
        report.put("cases", cases); report.put("heapMaxBytes", Runtime.getRuntime().maxMemory());
        report.put("heapUsedBytesAfterQueries", Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        report.put("mappedForecastBytes", (long)1440*721*7*4*29);
        report.put("limitations", "Three ocean-node engineering scenarios, not port-to-port weather coverage, calibrated ETA, continuous optimality or an operational vessel test");
        Files.write(output.resolve("runtime-report.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
        Files.write(output.resolve("port-weather-coverage.json"), mapper.writeValueAsBytes(census));
        for (var row : cases) {
            assertThat(row.get("httpStatus")).as(row.get("basin").toString()).isEqualTo(200);
            var response = (JsonNode)row.get("response");
            assertThat(response.path("fuelTonnes").isNull()).isTrue(); assertThat(response.path("emissionsTonnes").isNull()).isTrue();
            var route = response.path("route"); assertThat(route.path("status").asText()).isEqualTo("FOUND_RESEARCH_SCENARIO");
            double total = Duration.between(departure, Instant.parse(route.path("arrival").asText())).getSeconds();
            assertThat(route.path("sailingSeconds").asDouble()+route.path("modelledWaitingSeconds").asDouble()).isCloseTo(total, within(1e-5));
            for (var leg : route.path("legs")) if (!leg.path("edgeId").isNull())
                assertThat(policy.evaluate(graph.edges().get(leg.path("edgeId").asInt())).allowed()).isTrue();
        }
        ObjectNode invalid = firstRequest.deepCopy(); invalid.put("expectedForecastVersion", "wrong");
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json").content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value("FORECAST_VERSION_MISMATCH"));
        invalid = firstRequest.deepCopy(); invalid.put("departure", weather.end().plusSeconds(1).toString());
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json").content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value("DEPARTURE_OUTSIDE_FORECAST_HORIZON"));
        invalid = firstRequest.deepCopy(); invalid.put("mode", "CURRENT_FORECAST_RESEARCH");
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json").content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isConflict());
        invalid = firstRequest.deepCopy(); ((ObjectNode)invalid.path("vessel")).remove("headWaveLossKnotsPerM2");
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json").content(mapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest());
    }
    private static int nearest(PhysicalGraph graph, JsonNode coordinate) {
        var point = new PhysicalGraph.Point(coordinate.get(0).asDouble(), coordinate.get(1).asDouble());
        double distance = Double.POSITIVE_INFINITY; int index = -1;
        for (int i = 0; i < graph.nodes().size(); i++) {
            double candidate = point.distanceTo(graph.nodes().get(i).point());
            if (candidate < distance) { distance = candidate; index = i; }
        }
        return index;
    }
}
