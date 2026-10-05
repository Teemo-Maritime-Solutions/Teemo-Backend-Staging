package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.*;

@RestController
@RequestMapping("/api/v2/maritime")
public class WeatherRoutingController {
    private final GraphCatalog graphs;
    private final ForecastCatalog forecasts;
    private final Semaphore searchPermit = new Semaphore(1);
    public WeatherRoutingController(GraphCatalog graphs, ForecastCatalog forecasts) { this.graphs = graphs; this.forecasts = forecasts; }
    public record VesselInput(Double calmSpeedKnots, Double headWaveLossKnotsPerM2, Double beamWaveLossKnotsPerM2,
                              Double followingWaveLossKnotsPerM2, Double headWindLossKnotsPerMps2,
                              Double maximumWaveHeightM, Double maximumWindMps) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported vessel parameter"); }
        VesselModel model() {
            for (Double v : Arrays.asList(calmSpeedKnots, headWaveLossKnotsPerM2, beamWaveLossKnotsPerM2,
                    followingWaveLossKnotsPerM2, headWindLossKnotsPerMps2, maximumWaveHeightM, maximumWindMps))
                if (v == null) throw new IllegalArgumentException("Every vessel parameter must be explicitly supplied, including zeros");
            return new VesselModel(calmSpeedKnots, headWaveLossKnotsPerM2, beamWaveLossKnotsPerM2,
                    followingWaveLossKnotsPerM2, headWindLossKnotsPerMps2, maximumWaveHeightM, maximumWindMps);
        }
    }
    public record Request(String originNodeId, String destinationNodeId, Instant departure,
                          String expectedGraphVersion, String expectedForecastVersion, String mode,
                          Boolean acknowledgeResearchLimitations, VesselInput vessel,
                          TemporalRouteSearch.Options search, Double draftM, Double underKeelClearanceM,
                          Set<Integer> closedEdgeIds, Set<String> prohibitedZones, Boolean requireKnownLegalStatus) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported weather route parameter"); }
    }
    @GetMapping("/weather") public ResponseEntity<?> weather() {
        var available = forecasts.current();
        if (available.isEmpty()) return error(503, forecasts.status());
        var forecast = available.get(); var body = new LinkedHashMap<String, Object>();
        body.put("status", "RESEARCH_FORECAST"); body.put("forecastVersion", forecast.version());
        body.put("forecast", forecast.manifest()); body.put("expired", Instant.now().isAfter(forecast.end()));
        body.put("freshForCurrentForecastMode", !Instant.now().isAfter(forecast.start().plusSeconds(48*3600))
                && !Instant.now().isBefore(forecast.start()) && !Instant.now().isAfter(forecast.end()));
        return ResponseEntity.ok(body);
    }
    @PostMapping("/weather-routes") public ResponseEntity<?> route(@RequestBody Request request) {
        try {
            if (!Boolean.TRUE.equals(request.acknowledgeResearchLimitations())) return error(400, "RESEARCH_ACKNOWLEDGMENT_REQUIRED");
            if (request.vessel() == null || request.search() == null || request.departure() == null
                    || request.requireKnownLegalStatus() == null) return error(400, "EXPLICIT_MODEL_AND_CONSTRAINTS_REQUIRED");
            var vessel = request.vessel().model();
            var constraints = new RouteCostPolicy.Constraints(request.draftM(), request.underKeelClearanceM(),
                    request.closedEdgeIds(), request.prohibitedZones(), request.requireKnownLegalStatus());
            if (!Set.of("CURRENT_FORECAST_RESEARCH", "HISTORICAL_RESEARCH_REPLAY").contains(request.mode() == null ? "" : request.mode()))
                return error(400, "EXPLICIT_FORECAST_MODE_REQUIRED");
            var weather = forecasts.current();
            if (weather.isEmpty()) return error(503, forecasts.status());
            ForecastSnapshot forecast = weather.get();
            if (!forecast.version().equals(request.expectedForecastVersion())) return error(409, "FORECAST_VERSION_MISMATCH");
            Instant now = Instant.now();
            if ("CURRENT_FORECAST_RESEARCH".equals(request.mode())
                    && (now.isBefore(forecast.start()) || now.isAfter(forecast.start().plusSeconds(48*3600))
                    || now.isAfter(forecast.end()) || request.departure().isBefore(now)))
                return error(409, "FORECAST_STALE_OR_DEPARTURE_PAST_USE_EXPLICIT_REPLAY");
            if (request.departure().isBefore(forecast.start()) || !request.departure().isBefore(forecast.end()))
                return error(422, "DEPARTURE_OUTSIDE_FORECAST_HORIZON");
            var available = graphs.current();
            if (available.isEmpty()) return error(503, graphs.status());
            PhysicalGraph graph = available.get();
            if (!graph.version().equals(request.expectedGraphVersion())) return error(409, "GRAPH_VERSION_MISMATCH");
            int origin = findNode(graph, request.originNodeId()), destination = findNode(graph, request.destinationNodeId());
            if (origin < 0 || destination < 0) return error(422, "ENDPOINT_NOT_IN_PHYSICAL_GRAPH");
            for (int edgeId : constraints.closedEdgeIds())
                if (edgeId < 0 || edgeId >= graph.edges().size()) return error(400, "UNKNOWN_CLOSED_EDGE");
            if (constraints.prohibitedZones().size() > 100 || constraints.closedEdgeIds().size() > 100000)
                return error(400, "TOO_MANY_CONSTRAINT_IDENTIFIERS");
            for (String zone : constraints.prohibitedZones())
                if (graph.edges().stream().noneMatch(e -> zone.equals(e.specialZone()))) return error(400, "UNKNOWN_PROHIBITED_ZONE");
            if (!searchPermit.tryAcquire()) return error(429, "WEATHER_SEARCH_BUSY");
            TemporalRouteSearch.Result result;
            try { result = new TemporalRouteSearch(graph, forecast, vessel, constraints, request.search())
                    .search(origin, destination, request.departure()); }
            finally { searchPermit.release(); }
            var body = new LinkedHashMap<String, Object>();
            body.put("status", result.status()); body.put("graphVersion", graph.version());
            body.put("forecastVersion", forecast.version()); body.put("forecastRun", forecast.start().toString());
            body.put("forecastValidUntil", forecast.end().toString()); body.put("mode", request.mode());
            body.put("vesselModel", vessel); body.put("vesselModelEvidence", "USER_DECLARED_RESEARCH_SCENARIO_NOT_CALIBRATED");
            body.put("search", request.search()); body.put("physicalConstraints", constraints); body.put("route", result);
            body.put("geometry", WeatherRoutePresentation.geometry(graph, result));
            body.put("regionalControls", graph.manifest().path("regionalControls"));
            body.put("edgeCoverage", result.legs().stream().filter(l -> l.edgeId() != null).map(l -> {
                var edge = graph.edges().get(l.edgeId());
                return Map.of("edgeId", edge.id(), "regionalControlIds", edge.regionalControlIds(),
                        "scope", edge.regionalControlIds().isEmpty() ? "GLOBAL_COASTLINE_RESEARCH" : "REGIONAL_CHART_CONTROLS_RESEARCH");
            }).toList());
            body.put("fuelTonnes", null); body.put("emissionsTonnes", null);
            body.put("missingData", List.of("CALIBRATED_VESSEL_PERFORMANCE", "FUEL_CURVE", "GLOBAL_OPERATIONAL_LEGAL_DEPTH_COVERAGE", "VALIDATED_HOLDING_LOCATIONS"));
            body.put("limitations", List.of("Nearest forecast cell; missing values block traversal; no coastal filling",
                    "Explicit Euler integration with at most 2500 metres and 300 seconds per step; sampled limits only",
                    "Mean wave period is retained but not used by this declared response model",
                    "Arrival rounding and waiting assume holding at graph nodes; no anchorage permission is inferred",
                    "ETA is an uncalibrated scenario result; no consumption or emissions estimate"));
            int status = "SEARCH_BUDGET_EXHAUSTED".equals(result.status()) ? 422
                    : result.arrival() == null ? 422 : 200;
            return ResponseEntity.status(status).body(body);
        } catch (IllegalArgumentException invalid) { return error(400, "INVALID_WEATHER_ROUTE_PARAMETERS"); }
    }
    private static int findNode(PhysicalGraph graph, String id) {
        if (id == null || id.length() > 180) return -1;
        var port = graph.ports().get(id);
        if (port != null && !"CONNECTED_REFERENCE_POINT".equals(port.status())) return -1;
        for (int i = 0; i < graph.nodes().size(); i++) if (graph.nodes().get(i).id().equals(id)) return i;
        return -1;
    }
    private static ResponseEntity<?> error(int status, String code) { return ResponseEntity.status(status).body(Map.of("status", code)); }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidJson() { return error(400, "INVALID_JSON_OR_UNSUPPORTED_FIELD"); }
}
