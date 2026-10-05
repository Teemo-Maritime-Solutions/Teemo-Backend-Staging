package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WeatherRoutingTest {
    static final Instant START = Instant.parse("2026-09-22T00:00:00Z");
    static final VesselModel VESSEL = new VesselModel(10, .1, .05, 0, 0, 10, 40);
    static final RouteCostPolicy.Constraints CONSTRAINTS = new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false);
    static final TemporalRouteSearch.Options OPTIONS = new TemporalRouteSearch.Options(60, 2, 5000, 10000, 30, true);
    @TempDir Path directory;
    static WeatherField.Sample sample(double height, double direction, double east, double north) {
        return new WeatherField.Sample(height, direction, 8, 0, 0, east, north);
    }
    static WeatherField field(java.util.function.BiFunction<PhysicalGraph.Point, Instant, WeatherField.Sample> samples) {
        return new WeatherField() {
            public Sample sample(PhysicalGraph.Point p, Instant t) {
                if (t.isBefore(start()) || t.isAfter(end())) throw new Unavailable("FORECAST_HORIZON");
                return samples.apply(p, t);
            }
            public Instant start() { return START; }
            public Instant end() { return START.plusSeconds(7200); }
            public double maximumCurrentMps() { return 2; }
        };
    }
    static PhysicalGraph graph(Set<String> restrictions) {
        var a = new PhysicalGraph.Point(0, 0); var b = new PhysicalGraph.Point(0, .01);
        return new PhysicalGraph(List.of(new PhysicalGraph.Node("a", a, "OCEAN", List.of("SYNTHETIC_TEST")),
                new PhysicalGraph.Node("b", b, "OCEAN", List.of("SYNTHETIC_TEST"))),
                List.of(new PhysicalGraph.Edge(0, 0, 1, a.distanceTo(b), List.of(a, b), null, "OCEAN", null,
                        restrictions, List.of("SYNTHETIC_TEST"), "UNKNOWN", List.of("NY_TEST_CONTROL"))),
                List.of(), new ObjectMapper().createObjectNode().put("graphVersion", "SYNTHETIC_TEST"));
    }
    @Test void waveFromConventionAndCrossCurrentCompensation() {
        assertThat(VESSEL.groundSpeedMps(sample(2, 0, 0, 0), 0)).isLessThan(VESSEL.groundSpeedMps(sample(2, 180, 0, 0), 0));
        assertThat(VESSEL.groundSpeedMps(sample(0, 0, 0, 1), 0)).isCloseTo(10*VesselModel.KNOT_MPS+1, within(1e-9));
        assertThat(VESSEL.groundSpeedMps(sample(0, 0, 1, 0), 0)).isCloseTo(Math.sqrt(Math.pow(10*VesselModel.KNOT_MPS, 2)-1), within(1e-9));
        assertThatThrownBy(() -> VESSEL.groundSpeedMps(sample(0, 0, 6, 0), 0)).hasMessage("CANNOT_HOLD_COURSE");
        assertThatThrownBy(() -> VESSEL.groundSpeedMps(sample(11, 0, 0, 0), 0)).hasMessage("WAVE_LIMIT");
        assertThatThrownBy(() -> new WeatherField.Sample(Double.NaN, 0, 1, 0, 0, 0, 0)).hasMessage("WEATHER_MISSING");
    }
    @Test void headWindIsAgainstTravelDirection() {
        var model = new VesselModel(10, 0, 0, 0, .01, 10, 40);
        assertThat(model.groundSpeedMps(new WeatherField.Sample(0, 0, 8, 0, -10, 0, 0), 0))
                .isCloseTo(9*VesselModel.KNOT_MPS, within(1e-9));
        assertThat(model.groundSpeedMps(new WeatherField.Sample(0, 0, 8, 0, 10, 0, 0), 0))
                .isCloseTo(10*VesselModel.KNOT_MPS, within(1e-9));
    }
    @Test void laterLabelsSurviveWhenAnEarlierArrivalCannotTraverse() {
        var weather = field((p, t) -> sample(p.latitude() > .001 && t.isBefore(START.plusSeconds(600)) ? 20 : 0, 0, 0, 0));
        var result = new TemporalRouteSearch(graph(Set.of()), weather, VESSEL, CONSTRAINTS, OPTIONS).search(0, 1, START);
        assertThat(result.status()).isEqualTo("FOUND_RESEARCH_SCENARIO");
        assertThat(result.legs()).anyMatch(l -> l.edgeId() == null);
        assertThat(result.legs().stream().filter(l -> l.edgeId() != null).findFirst().orElseThrow().departure()).isAfter(START);
        assertThat(result.modelledWaitingSeconds()).isPositive();
        assertThat(result.sailingSeconds()+result.modelledWaitingSeconds())
                .isCloseTo((double)Duration.between(START, result.arrival()).getSeconds(), within(1e-6));
    }
    @Test void canalLocksNeedASeparateTransitTimeModelEvenWithGoodWeather() {
        var base = graph(Set.of());
        var edges = base.edges().stream().map(e -> new PhysicalGraph.Edge(e.id(), e.from(), e.to(), e.distanceM(), e.geometry(),
                null, "CANAL_LOCK_RESEARCH", "PANAMA_CANAL_RESEARCH", Set.of(), List.of("fixture"), "UNKNOWN")).toList();
        var canal = new PhysicalGraph(base.nodes(), edges, List.copyOf(base.ports().values()), base.manifest());
        var result = new TemporalRouteSearch(canal, field((p,t) -> sample(0,0,0,0)), VESSEL, CONSTRAINTS, OPTIONS).search(0,1,START);
        assertThat(result.arrival()).isNull();
        assertThat(result.rejectedEvaluations()).containsKey("CANAL_LOCK_TRANSIT_TIME_MODEL_UNAVAILABLE");
    }
    @Test void suezNeedsASeparateTransitTimeModelEvenWithGoodWeather() {
        var base = graph(Set.of());
        var edges = base.edges().stream().map(e -> new PhysicalGraph.Edge(e.id(), e.from(), e.to(), e.distanceM(), e.geometry(),
                null, "CANAL_CENTERLINE_RESEARCH", "SUEZ_CANAL_RESEARCH", Set.of(), List.of("fixture"), "UNKNOWN")).toList();
        var canal = new PhysicalGraph(base.nodes(), edges, List.copyOf(base.ports().values()), base.manifest());
        var result = new TemporalRouteSearch(canal, field((p,t) -> sample(0,0,0,0)), VESSEL, CONSTRAINTS, OPTIONS).search(0,1,START);
        assertThat(result.arrival()).isNull();
        assertThat(result.rejectedEvaluations()).containsKey("SUEZ_TRANSIT_TIME_MODEL_UNAVAILABLE");
    }
    @Test void regionalHardRestrictionsAreNeverOverriddenByGoodWeather() {
        var result = new TemporalRouteSearch(graph(Set.of("UNRESOLVED_ENC_RESTRICTION")),
                field((p, t) -> sample(0, 0, 0, 0)), VESSEL, CONSTRAINTS, OPTIONS).search(0, 1, START);
        assertThat(result.arrival()).isNull();
        assertThat(result.rejectedEvaluations()).containsKey("UNRESOLVED_HARD_RESTRICTION");
    }
    @Test void missingWeatherHorizonAndBudgetNeverReturnSuccessfulRoutes() {
        var weather = field((p, t) -> { throw new WeatherField.Unavailable("WEATHER_MISSING"); });
        var search = new TemporalRouteSearch(graph(Set.of()), weather, VESSEL, CONSTRAINTS, OPTIONS);
        assertThat(search.search(0, 1, START).status()).isEqualTo("NO_ROUTE_WITHIN_MODEL_HORIZON");
        assertThatThrownBy(() -> search.search(0, 1, START.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        var budget = new TemporalRouteSearch.Options(60, 2, 1, 1, 30, true);
        assertThat(new TemporalRouteSearch(graph(Set.of()), field((p, t) -> sample(0, 0, 0, 0)), VESSEL, CONSTRAINTS, budget)
                .search(0, 1, START).status()).isEqualTo("SEARCH_BUDGET_EXHAUSTED");
        assertThatThrownBy(() -> new TemporalRouteSearch.Options(60, 2, 100, 100, 30, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
    Path fixture() throws Exception {
        var mapper = new ObjectMapper();
        Files.write(directory.resolve("source-receipt.json"), mapper.writeValueAsBytes(Map.of("probeOnly", false,
                "runTime", START.toString(), "source", "ECMWF_IFS_OPEN_DATA", "fixture", "SYNTHETIC_TEST_ONLY")));
        var frames = new ArrayList<Map<String, Object>>();
        for (int frame = 0; frame < 2; frame++) {
            var buffer = ByteBuffer.allocate(4*3*7*4).order(ByteOrder.LITTLE_ENDIAN);
            for (int field = 0; field < 7; field++) for (int cell = 0; cell < 12; cell++)
                buffer.putFloat(cell == 0 ? Float.NaN : field == 1 ? frame == 0 ? 359 : 1 : field == 2 ? 8 : field == 0 ? 2 : 0);
            Path path = directory.resolve(frame == 0 ? "frame-000.f32" : "frame-006.f32"); Files.write(path, buffer.array());
            frames.add(Map.of("file", path.getFileName().toString(), "forecastHour", frame*6,
                    "bytes", buffer.capacity(), "sha256", ForecastSnapshot.sha256(path)));
        }
        var manifest = mapper.createObjectNode();
        manifest.put("schemaVersion", 1).put("purpose", "REAL_FORECAST_RESEARCH_ONLY").put("source", "ECMWF_IFS_OPEN_DATA")
                .put("layout", "FIELD_LAT_LON_FLOAT32_LE_NAN_MISSING").put("waveDirectionConvention", "FROM_TRUE_NORTH_CLOCKWISE")
                .put("spatialSampling", "NEAREST_CELL_NO_MISSING_FILL").put("temporalSampling", "LINEAR_CIRCULAR_DIRECTION")
                .put("nx", 4).put("ny", 3).put("resolutionDegrees", 90).put("longitudeFirst", 180).put("latitudeFirst", 90)
                .put("stepHours", 6).put("runTime", START.toString()).put("maximumCurrentMps", 0)
                .put("sourceReceiptSha256", ForecastSnapshot.sha256(directory.resolve("source-receipt.json")));
        manifest.set("fields", mapper.valueToTree(ForecastSnapshot.FIELDS)); manifest.set("frames", mapper.valueToTree(frames));
        manifest.set("units", mapper.valueToTree(List.of("m", "Degree true", "s", "m s**-1", "m s**-1", "m s**-1", "m s**-1")));
        Path path = directory.resolve("manifest.json"); Files.write(path, mapper.writeValueAsBytes(manifest)); return path;
    }
    @Test void circularInterpolationDatelineMissingAndEndOfForecast() throws Exception {
        Path path = fixture(); var forecast = new ForecastSnapshot(path, ForecastSnapshot.sha256(path));
        var a = forecast.sample(new PhysicalGraph.Point(180, 0), START.plusSeconds(10800));
        assertThat(a.waveFromDegrees()).isCloseTo(0, within(1e-8));
        assertThat(a).isEqualTo(forecast.sample(new PhysicalGraph.Point(-180, 0), START.plusSeconds(10800)));
        assertThatThrownBy(() -> forecast.sample(new PhysicalGraph.Point(180, 90), START)).hasMessage("WEATHER_MISSING");
        assertThat(forecast.sample(new PhysicalGraph.Point(0, 0), forecast.end()).waveFromDegrees()).isCloseTo(1, within(1e-8));
        assertThatThrownBy(() -> forecast.sample(new PhysicalGraph.Point(0, 0), forecast.end().plusNanos(1))).hasMessage("FORECAST_HORIZON");
    }
    @Test void corruptFramesAndUnderstatedCurrentBoundAreRejected() throws Exception {
        Path path = fixture(); String pin = ForecastSnapshot.sha256(path);
        Path frame = directory.resolve("frame-000.f32"); byte[] bytes = Files.readAllBytes(frame);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putFloat((5*12+1)*4, 2); Files.write(frame, bytes);
        assertThatThrownBy(() -> new ForecastSnapshot(path, pin)).hasMessage("Forecast frame integrity failed");
        var mapper = new ObjectMapper(); var manifest = mapper.readTree(path.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("frames").get(0)).put("sha256", ForecastSnapshot.sha256(frame));
        Files.write(path, mapper.writeValueAsBytes(manifest));
        assertThatThrownBy(() -> new ForecastSnapshot(path, ForecastSnapshot.sha256(path))).hasMessage("Invalid forecast current bound");
    }
    @Test void controllerRejectsIncompleteDeclarationsAndUnavailableForecast() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new WeatherRoutingController(new GraphCatalog("", 1000), new ForecastCatalog("", ""))).build();
        mvc.perform(get("/api/v2/maritime/weather")).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("RESEARCH_ACKNOWLEDGMENT_REQUIRED"));
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json")
                .content("{\"acknowledgeResearchLimitations\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("EXPLICIT_MODEL_AND_CONSTRAINTS_REQUIRED"));
        mvc.perform(post("/api/v2/maritime/weather-routes").contentType("application/json")
                .content("{\"acknowledgeResearchLimitations\":true,\"ignoreRestrictions\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("INVALID_JSON_OR_UNSUPPORTED_FIELD"));
    }
}
