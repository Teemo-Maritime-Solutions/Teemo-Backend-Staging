package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v2/maritime")
public class MaritimeRoutingController {
    private final GraphCatalog catalog;
    public MaritimeRoutingController(GraphCatalog catalog) { this.catalog = catalog; }

    public record Request(String originPortId, String destinationPortId, Integer k, Integer maxCandidates,
                          Long maxExpansions, Double minimumSeparationM, boolean acknowledgeResearchLimitations,
                          Double userSuppliedSpeedKnots, Double userSuppliedDraftM, Double userSuppliedClearanceM,
                          Set<Integer> userSuppliedClosedEdgeIds, Set<String> userSuppliedProhibitedZones,
                          boolean requireKnownLegalStatus, String expectedGraphVersion) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new IllegalArgumentException("Unsupported routing parameter: " + name);
        }
    }
    public record Route(List<String> nodeIds, List<Integer> edgeIds, double distanceM, Double estimatedHours,
                        List<List<List<Double>>> geometry, String geometryType, List<String> explanation,
                        Coverage coverage) {}
    public record CoverageSpan(int firstEdgeIndex, int lastEdgeIndex, String scope, List<String> regionalControlIds) {}
    public record Coverage(String purpose, String graphVersion, Object regionalControls,
                           List<CoverageSpan> spans, List<String> missingData) {}

    @GetMapping("/graph")
    public ResponseEntity<?> graph() {
        return catalog.current().<ResponseEntity<?>>map(g -> {
            var manifest = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(g.manifest(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return respond(g, 200, manifest);
        })
                .orElseGet(() -> unavailable(catalog.status()));
    }
    @GetMapping("/provenance")
    public ResponseEntity<?> provenance() {
        return catalog.current().<ResponseEntity<?>>map(g -> respond(g, 200, Map.of("graphVersion", g.version(),
                "sources", g.manifest().path("sources"), "parameters", g.manifest().path("parameters"))))
                .orElseGet(() -> unavailable(catalog.status()));
    }
    @GetMapping("/ports")
    public ResponseEntity<?> ports(@RequestParam(defaultValue = "CONNECTED_REFERENCE_POINT") String status,
                                   @RequestParam(defaultValue = "0") int offset,
                                   @RequestParam(defaultValue = "50") int limit) {
        if (offset < 0 || limit < 1 || limit > 200) return badRequest("offset >= 0; limit in [1, 200]");
        return catalog.current().<ResponseEntity<?>>map(g -> {
            List<PhysicalGraph.Port> selected = g.ports().values().stream().filter(p -> status.equals("ALL") || p.status().equals(status)).toList();
            return respond(g, 200, Map.of("graphVersion", g.version(), "total", selected.size(), "offset", offset,
                    "ports", selected.stream().skip(offset).limit(limit).toList()));
        }).orElseGet(() -> unavailable(catalog.status()));
    }

    @PostMapping("/routes")
    public ResponseEntity<?> routes(@RequestBody Request request) {
        if (!request.acknowledgeResearchLimitations()) return respond(null, 422, Map.of(
                "status", "OPERATIONAL_VALIDATION_UNAVAILABLE", "message", "Research-only graph; explicit acknowledgement required. Not for navigation."));
        Optional<PhysicalGraph> snapshot = catalog.current();
        if (snapshot.isEmpty()) return unavailable(catalog.status());
        PhysicalGraph graph = snapshot.get();
        try {
            if (request.expectedGraphVersion() != null && !request.expectedGraphVersion().equals(graph.version()))
                return respond(graph, 409, Map.of("status", "GRAPH_VERSION_MISMATCH",
                        "message", "Request targets a different graph snapshot; refresh source IDs and constraints"));
            if (request.originPortId() == null || request.destinationPortId() == null) return badRequest("Port IDs required");
            if (request.originPortId().equals(request.destinationPortId())) return badRequest("Distinct source ports required");
            PhysicalGraph.Port start = graph.ports().get(request.originPortId()), end = graph.ports().get(request.destinationPortId());
            if (start == null || end == null) return respond(graph, 404, Map.of("status", "PORT_NOT_IN_SOURCE_CATALOG"));
            if (!"CONNECTED_REFERENCE_POINT".equals(start.status()) || !"CONNECTED_REFERENCE_POINT".equals(end.status()))
                return respond(graph, 422, Map.of("status", "PORT_ACCESS_UNAVAILABLE", "graphVersion", graph.version(),
                        "originStatus", start.status(), "destinationStatus", end.status()));
            Double speed = request.userSuppliedSpeedKnots();
            if (speed != null && (!Double.isFinite(speed) || speed <= 0)) return badRequest("USER_SUPPLIED speed must be finite and positive");
            int k = request.k() == null ? 1 : request.k();
            int candidates = request.maxCandidates() == null ? Math.max(k, 20) : request.maxCandidates();
            long budget = request.maxExpansions() == null ? 2_000_000L : request.maxExpansions();
            double separation = request.minimumSeparationM() == null ? 50000 : request.minimumSeparationM();
            RouteCostPolicy.Constraints constraints = new RouteCostPolicy.Constraints(request.userSuppliedDraftM(),
                    request.userSuppliedClearanceM(), request.userSuppliedClosedEdgeIds(), request.userSuppliedProhibitedZones(), request.requireKnownLegalStatus());
            if (!constraints.closedEdgeIds().isEmpty() && request.expectedGraphVersion() == null)
                return respond(graph, 400, Map.of("status", "GRAPH_VERSION_REQUIRED_FOR_EDGE_CONSTRAINTS",
                        "message", "Edge IDs are snapshot-local; supply expectedGraphVersion"));
            if (constraints.closedEdgeIds().stream().anyMatch(id -> id < 0 || id >= graph.edges().size())) return badRequest("Unknown closed edge ID for this graph version");
            if (!constraints.prohibitedZones().isEmpty()) {
                Set<String> knownZones = new HashSet<>();
                graph.edges().forEach(e -> { if (e.specialZone() != null) knownZones.add(e.specialZone()); });
                if (!knownZones.containsAll(constraints.prohibitedZones())) return badRequest("Unknown zone ID; unavailable zone data cannot enforce this prohibition");
            }
            RouteSearch.Result result = new RouteSearch(graph, RouteCostPolicy.distance(constraints), budget)
                    .alternatives(start.nodeId(), end.nodeId(), k, candidates, separation);
            List<Route> routes = result.paths().stream().map(p -> resource(graph, p, speed)).toList();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", routes.isEmpty() ? (result.budgetExhausted() ? "SEARCH_BUDGET_EXHAUSTED" : "NO_FEASIBLE_RESEARCH_ROUTE") : "RESEARCH_ONLY");
            response.put("graphVersion", graph.version()); response.put("sources", graph.manifest().path("sources"));
            response.put("dataDate", graph.manifest().path("createdAt")); response.put("confidence", "NOT_CALIBRATED_NOT_NAVIGATIONAL");
            List<String> missing = new ArrayList<>(); graph.manifest().path("report").path("missingData").forEach(n -> missing.add(n.asText()));
            if (speed == null) missing.add("speed_for_time_estimate");
            missing.add("buyer_seller_exposure_requires_research_gate_and_contractual_inputs");
            response.put("missingData", missing);
            response.put("assumptions", List.of("Coastline-relative geometric feasibility only; no legal/depth/ice/weather assurance",
                    "Mesh direction is a planning possibility, not observed AIS direction", "No fuel, emission, tariff or probability estimates",
                    "USER_SUPPLIED closures are static for the whole route; absence of supplied closures does not prove waters are open",
                    speed == null ? "Travel time unavailable" : "USER_SUPPLIED constant speed over ground; excludes waits, weather and port operations. Canal routes have no time estimate without a lock transit model"));
            response.put("inputs", request);
            response.put("inputProvenance", Map.of("source", "USER_SUPPLIED", "receivedAt", java.time.Instant.now().toString(),
                    "license", "NOT_SPECIFIED_NO_REDISTRIBUTION_RIGHTS_ASSUMED", "coverage", "This request only",
                    "units", "draft/clearance/separation: m; speed: kn; IDs: this graph version",
                    "limitations", "Unverified user declarations; observation date not supplied"));
            response.put("routes", routes); response.put("requestedK", k); response.put("candidatePaths", result.candidates());
            response.put("algorithm", "A_STAR_YEN_WITH_GEOGRAPHIC_EXCLUSION_CANDIDATES");
            response.put("expandedNodes", result.expandedNodes()); response.put("budgetExhausted", result.budgetExhausted());
            response.put("rejectedEdgeEvaluations", result.rejectedEvaluations()); response.put("rejectedForSimilarity", result.rejectedForSimilarity());
            response.put("minimumSeparationM", separation); response.put("diversityCriterion", "CONSERVATIVE_GEODESIC_SEPARATION_NOT_HOMOTOPY_PROOF");
            response.put("recommendation", routes.isEmpty() ? "None" : "Shortest modelled distance among returned feasible research candidates; not an operational recommendation");
            return respond(graph, routes.isEmpty() ? 422 : 200, response);
        } catch (IllegalArgumentException e) { return badRequest(e.getMessage()); }
    }

    private Route resource(PhysicalGraph graph, RouteSearch.Path path, Double speed) {
        List<List<List<Double>>> parts = new ArrayList<>();
        List<List<Double>> part = new ArrayList<>();
        double step = graph.manifest().path("parameters").path("geodesic_step_m").asDouble();
        if (!Double.isFinite(step) || step <= 0 || step > 10000) throw new IllegalArgumentException("Invalid artifact geometry step");
        PhysicalGraph.Point origin = graph.nodes().get(path.nodes().get(0)).point();
        part.add(List.of(origin.longitude(), origin.latitude()));
        for (PhysicalGraph.Edge edge : path.edges()) for (int i = 1; i < edge.geometry().size(); i++) {
            PhysicalGraph.Point a = edge.geometry().get(i - 1), b = edge.geometry().get(i);
            GeodesicData inverse = Geodesic.WGS84.Inverse(a.latitude(), a.longitude(), b.latitude(), b.longitude());
            // Representation engineering parameter, not a physical observation.
            int segments = Math.max(1, (int) Math.ceil(inverse.s12 / step));
            for (int s = 1; s <= segments; s++) {
                GeodesicData point = Geodesic.WGS84.Direct(a.latitude(), a.longitude(), inverse.azi1, inverse.s12 * s / segments);
                List<Double> next = s == segments ? List.of(b.longitude(), b.latitude()) : List.of(point.lon2, point.lat2);
                List<Double> previous = part.get(part.size() - 1);
                if (Math.abs(next.get(0) - previous.get(0)) > 180) {
                    double adjusted = next.get(0) + (next.get(0) < previous.get(0) ? 360 : -360);
                    double seam = previous.get(0) >= 0 ? 180 : -180;
                    double lat = previous.get(1) + (next.get(1) - previous.get(1)) * (seam - previous.get(0)) / (adjusted - previous.get(0));
                    part.add(List.of(seam, lat)); parts.add(List.copyOf(part)); part = new ArrayList<>(); part.add(List.of(-seam, lat));
                }
                part.add(next);
            }
        }
        parts.add(List.copyOf(part));
        boolean panama = path.edges().stream().anyMatch(e -> "PANAMA_CANAL_RESEARCH".equals(e.specialZone()));
        boolean suez = path.edges().stream().anyMatch(e -> "SUEZ_CANAL_RESEARCH".equals(e.specialZone()));
        Double hours = speed == null || panama || suez ? null : path.distanceM() / (1852 * speed);
        if (hours != null && (!Double.isFinite(hours) || hours <= 0)) throw new IllegalArgumentException("Speed/duration numeric magnitude unsupported");
        List<CoverageSpan> spans = new ArrayList<>();
        for (int i = 0; i < path.edges().size(); i++) {
            var regionalIds = path.edges().get(i).regionalControlIds();
            String scope = ("PANAMA_CANAL_RESEARCH".equals(path.edges().get(i).specialZone())
                    || "SUEZ_CANAL_RESEARCH".equals(path.edges().get(i).specialZone())) ? "SOURCED_CANAL_RESEARCH"
                    : regionalIds.isEmpty() ? "GLOBAL_COASTLINE_RESEARCH" : "REGIONAL_CHART_CONTROLS_RESEARCH";
            if (!spans.isEmpty() && spans.get(spans.size() - 1).scope().equals(scope)
                    && spans.get(spans.size() - 1).regionalControlIds().equals(regionalIds)) {
                var previous = spans.remove(spans.size() - 1);
                spans.add(new CoverageSpan(previous.firstEdgeIndex(), i, scope, regionalIds));
            } else spans.add(new CoverageSpan(i, i, scope, regionalIds));
        }
        var control = graph.manifest().path("regionalControls");
        Coverage coverage = new Coverage("RESEARCH_NOT_NAVIGATION", graph.version(),
                control.isMissingNode() ? null : control, List.copyOf(spans),
                List.of("current_passage_permissions_and_closures", "vessel_depth_and_height_clearance", "weather_and_ice",
                        "worldwide_observed_trajectory_validation"));
        List<String> explanation = new ArrayList<>(List.of("All traversed edges exist in the source-checked artifact",
                "Hard constraints applied before relaxation", "A* ellipsoidal distance lower bound; contextual distance policy"));
        if (panama) explanation.add("PANAMA_CANAL_RESEARCH: source-supported classic-lock geometry; passage permission, vessel clearance and lock transit time are not validated. Canal geometry: OpenStreetMap contributors, ODbL-1.0.");
        if (suez) explanation.add("SUEZ_CANAL_RESEARCH: source-supported canal geometry; current passage permission, vessel clearance, convoy transit time and Red Sea security are not validated. Canal geometry: OpenStreetMap contributors, ODbL-1.0.");
        return new Route(path.nodes().stream().map(n -> graph.nodes().get(n).id()).toList(), path.signature(), path.distanceM(),
                hours, parts, "MultiLineString",
                List.copyOf(explanation), coverage);
    }
    private ResponseEntity<?> respond(PhysicalGraph graph, int status, Map<String, ?> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("graphVersion", graph == null ? null : graph.version());
        body.put("sources", graph == null ? List.of() : graph.manifest().path("sources"));
        body.put("dataDate", graph == null ? null : graph.manifest().path("createdAt"));
        body.put("confidence", graph == null ? "NOT_ASSESSED" : "NOT_CALIBRATED_NOT_NAVIGATIONAL");
        body.put("missingData", graph == null ? List.of("validated_graph_context") : graph.manifest().path("report").path("missingData"));
        body.put("assumptions", List.of("Research/planning only; no operational safety or permission assurance"));
        body.put("actorBreakdownAvailability", "GATED_ON_RESEARCH_VALIDATION_AND_CONTRACTUAL_INPUTS");
        body.putAll(payload);
        return ResponseEntity.status(status).body(body);
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidJson() { return badRequest("Invalid JSON, unsupported field or parameter type"); }

    private ResponseEntity<?> unavailable(String status) { return respond(null, 503, Map.of("status", status,
            "message", "Configure a locally built, versioned graph artifact. No synthetic fallback.")); }
    private ResponseEntity<?> badRequest(String message) { return respond(null, 400, Map.of("status", "INVALID_REQUEST", "message", message)); }
}
