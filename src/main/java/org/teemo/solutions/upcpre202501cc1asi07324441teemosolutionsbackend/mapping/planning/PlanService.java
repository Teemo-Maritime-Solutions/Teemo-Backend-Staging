package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning.PlanModel.*;

@Service
public class PlanService {
    public static final String VERSION = "INTEGRATED_RESEARCH_PLAN_1";
    private final PlanWeatherGateway weather;
    private final ContractEvaluator contracts;
    private final ScenarioSimulation simulations;
    private final PlanStore store;
    public PlanService(PlanWeatherGateway weather, ContractEvaluator contracts, ScenarioSimulation simulations, PlanStore store) {
        this.weather = weather; this.contracts = contracts; this.simulations = simulations; this.store = store;
    }
    public JsonNode create(Request request) { return build(request, null, null); }
    public JsonNode get(String id) { return store.get(id); }
    public JsonNode recalculate(String id, Recalculation request) {
        check(request != null, "RECALCULATION_REQUIRED"); text(request.commercialReviewEvidence(), "COMMERCIAL_REVIEW_REQUIRED");
        JsonNode parent = store.get(id);
        if (!parent.path("contentSha256").asText().equals(request.expectedParentContentSha256()))
            throw new PlanFailure(409, "PARENT_CONTENT_VERSION_MISMATCH");
        return build(request.replacement(), parent, request.commercialReviewEvidence());
    }
    private JsonNode build(Request request, JsonNode parent, String review) {
        validate(request); store.available();
        var routeHours = new TreeMap<String, BigDecimal>(); request.routes().forEach(r -> routeHours.put(r.id(), BigDecimal.ZERO));
        // Reject invalid economic/joint inputs before potentially expensive physical searches.
        simulations.evaluate(simulationInput(request, routeHours));
        var calculated = new TreeMap<String, JsonNode>();
        for (var route : request.routes().stream().sorted(Comparator.comparing(RouteInput::id)).toList()) {
            JsonNode result;
            try { result = weather.route(route.request()); }
            catch (PlanFailure failed) { throw new PlanFailure(failed.http, failed.getMessage(), Map.of("routeId", route.id(), "cause", failed.details == null ? failed.getMessage() : failed.details)); }
            if (!"FOUND_RESEARCH_SCENARIO".equals(result.path("status").asText()) || result.path("route").path("arrival").isNull())
                throw new PlanFailure(422, "PHYSICAL_ROUTE_NOT_FOUND", Map.of("routeId", route.id()));
            if (!route.request().expectedGraphVersion().equals(result.path("graphVersion").asText())
                    || !route.request().expectedForecastVersion().equals(result.path("forecastVersion").asText()))
                throw new PlanFailure(409, "PHYSICAL_RESULT_VERSION_MISMATCH");
            Instant arrival = Instant.parse(result.path("route").path("arrival").asText());
            Duration duration = Duration.between(route.request().departure(), arrival);
            check(!duration.isNegative() && !duration.isZero(), "INVALID_PHYSICAL_DURATION");
            BigDecimal seconds = BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9));
            routeHours.put(route.id(), seconds.divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP));
            calculated.put(route.id(), result);
        }
        var comparison = simulations.evaluate(simulationInput(request, routeHours));
        JsonNode provenance = weather.provenance(); var pin = request.routes().get(0).request();
        if (!pin.expectedGraphVersion().equals(provenance.path("graphVersion").asText())
                || !pin.expectedForecastVersion().equals(provenance.path("forecastVersion").asText()))
            throw new PlanFailure(409, "PROVENANCE_VERSION_MISMATCH");
        var bindingRows = PlanJson.MAPPER.createArrayNode();
        for (var alternative : request.alternatives().stream().sorted(Comparator.comparing(Alternative::id)).toList()) {
            var contract = contracts.evaluate(alternative.contract());
            var leg = alternative.contract().legs().stream().filter(l -> l.id().equals(alternative.carriageLegId())).findFirst().orElseThrow();
            var allocated = contract.legs().stream().filter(l -> l.legId().equals(leg.id())).findFirst().orElseThrow();
            var binding = bindingRows.addObject().put("alternativeId", alternative.id()).put("routeId", alternative.routeId())
                    .put("carriageLegId", leg.id()).put("fromEventId", leg.fromEventId()).put("toEventId", leg.toEventId())
                    .put("mappingEvidence", alternative.mappingEvidence()).put("mappingStatus", "USER_DECLARED_NOT_TERMINAL_VERIFIED")
                    .put("physicalElapsedHours", routeHours.get(alternative.routeId())).put("nonSailingHours", alternative.nonSailingHours())
                    .put("routingControl", leg.routingControl().name()).put("cargoRiskBearer", allocated.cargoRiskBearer().name())
                    .put("transportPayer", allocated.transportPayer().name()).put("routeContentSha256", PlanJson.sha(calculated.get(alternative.routeId())));
            binding.set("contractEvaluation", PlanJson.MAPPER.valueToTree(contract));
        }
        var content = PlanJson.MAPPER.createObjectNode().put("planVersion", VERSION).put("status", "INTEGRATED_RESEARCH_COMPARISON")
                .put("inputSha256", PlanJson.sha(request)).put("comparisonInputSha256", comparison.inputSha256());
        content.set("declaredInputs", PlanJson.MAPPER.valueToTree(request)); content.set("routes", PlanJson.MAPPER.valueToTree(calculated));
        content.set("bindings", bindingRows); content.set("comparison", PlanJson.MAPPER.valueToTree(comparison));
        content.set("provenance", provenance);
        var explanation = content.putObject("explanation").put("basis", "FINITE_DECLARED_DISTRIBUTION_PARETO_EXPLICIT_OBJECTIVES")
                .put("durationFormula", "physical sailing + modelled waiting + declared non-sailing + declared scenario additional hours")
                .put("durationRounding", "Physical elapsed hours rounded HALF_UP to 6 decimal places; at most 0.0018 seconds")
                .put("commercialAssumptions", "USER_DECLARED_NOT_AUTOMATICALLY_REPRICED")
                .put("authority", "Declared route control is reported; no actor permission or operational recommendation is established");
        explanation.set("paretoAlternativeIds", PlanJson.MAPPER.valueToTree(comparison.paretoAlternativeIds()));
        explanation.set("objectives", PlanJson.MAPPER.valueToTree(request.simulation().objectives()));
        explanation.set("alternativeMetricsAndDominators", PlanJson.MAPPER.valueToTree(comparison.alternatives()));
        content.set("missingData", PlanJson.MAPPER.valueToTree(List.of("EMPIRICALLY_CALIBRATED_PROBABILITIES_AND_VESSEL_ETA",
                "VERIFIED_CONTRACT_EVENT_TO_PHYSICAL_TERMINAL_ASSOCIATION", "ACTUAL_INSURANCE_RECOVERY", "FUEL_AND_EMISSIONS_CURVES",
                "WORLDWIDE_OPERATIONAL_PERMISSIONS_DEPTH_AND_HOLDING_VALIDATION")));
        content.set("limitations", PlanJson.MAPPER.valueToTree(List.of(
                "Weather validity covers the computed physical traversal only; added scenario hours are not rerouted or weather-validated",
                "Declared mapping covers one complete contractual leg; a risk transfer within that leg requires a different segmented model",
                "Costs, cargo damage and joint probabilities are not inferred from distance, route exposure or the forecast",
                "No failed alternative is silently removed; all requested physical routes must succeed before comparison is saved",
                "Physical constraints, missing-cell rejection and regional controls remain those of the frozen weather routing engine",
                "Stored content hash detects corruption but is not a digital signature or evidence of contractual authenticity")));
        if (parent != null) {
            content.putObject("parent").put("planId", parent.path("planId").asText()).put("contentSha256", parent.path("contentSha256").asText())
                    .put("commercialReviewEvidence", review);
            content.set("changes", changes(parent.path("content"), content));
        } else { content.putNull("parent"); content.putNull("changes"); }
        return store.save(content);
    }
    private void validate(Request request) {
        check(request != null, "PLAN_REQUEST_REQUIRED"); text(request.expectedPlanVersion(), "PLAN_VERSION_REQUIRED");
        if (!VERSION.equals(request.expectedPlanVersion())) throw new PlanFailure(409, "PLAN_VERSION_MISMATCH");
        check(Boolean.TRUE.equals(request.acknowledgeDeclaredEventMapping()), "EVENT_MAPPING_ACK_REQUIRED");
        check(request.routes() != null && !request.routes().isEmpty() && request.routes().size() <= 4, "ROUTES_REQUIRED_MAX_4");
        Set<String> routeIds = new HashSet<>(); int seconds = 0, states = 0, labels = 0;
        org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather.WeatherRoutingController.Request first = null;
        for (var route : request.routes()) {
            check(route != null && route.request() != null, "ROUTE_REQUIRED"); text(route.id(), "ROUTE_ID_REQUIRED");
            check(routeIds.add(route.id()), "DUPLICATE_ROUTE_ID"); var r = route.request();
            check(r.departure() != null && r.search() != null, "DEPARTURE_AND_SEARCH_REQUIRED");
            text(r.originNodeId(), "ORIGIN_REQUIRED"); text(r.destinationNodeId(), "DESTINATION_REQUIRED");
            text(r.expectedGraphVersion(), "GRAPH_PIN_REQUIRED"); text(r.expectedForecastVersion(), "FORECAST_PIN_REQUIRED");
            check((r.closedEdgeIds() == null || r.closedEdgeIds().stream().noneMatch(Objects::isNull))
                    && (r.prohibitedZones() == null || r.prohibitedZones().stream().noneMatch(Objects::isNull)), "NULL_RESTRICTION_IDENTIFIER");
            if (first == null) first = r;
            else check(Objects.equals(first.originNodeId(), r.originNodeId()) && Objects.equals(first.destinationNodeId(), r.destinationNodeId())
                    && first.departure().equals(r.departure()) && Objects.equals(first.mode(), r.mode())
                    && first.expectedGraphVersion().equals(r.expectedGraphVersion()) && first.expectedForecastVersion().equals(r.expectedForecastVersion()),
                    "ROUTES_REQUIRE_COMMON_ENDPOINTS_DEPARTURE_MODE_AND_VERSIONS");
            seconds += r.search().maximumRuntimeSeconds(); states += r.search().maximumExpandedStates(); labels += r.search().maximumLabels();
        }
        check(seconds <= 120 && states <= 400000 && labels <= 1000000, "AGGREGATE_ROUTE_BUDGET_EXCEEDED");
        check(request.alternatives() != null && !request.alternatives().isEmpty() && request.alternatives().size() <= 16, "ALTERNATIVES_REQUIRED_MAX_16");
        Set<String> usedRoutes = new HashSet<>(), ids = new HashSet<>();
        for (var a : request.alternatives()) {
            check(a != null, "ALTERNATIVE_REQUIRED"); text(a.id(), "ALTERNATIVE_ID_REQUIRED"); check(ids.add(a.id()), "DUPLICATE_ALTERNATIVE_ID");
            check(routeIds.contains(a.routeId()), "UNKNOWN_BOUND_ROUTE"); usedRoutes.add(a.routeId());
            text(a.mappingEvidence(), "MAPPING_EVIDENCE_REQUIRED"); hours(a.nonSailingHours());
            contracts.evaluate(a.contract());
            check(a.contract().legs().stream().anyMatch(l -> l.id().equals(a.carriageLegId())), "UNKNOWN_CONTRACTUAL_CARRIAGE_LEG");
        }
        check(usedRoutes.equals(routeIds), "UNUSED_PHYSICAL_ROUTE");
        check(request.simulation() != null && request.simulation().distribution() != null, "SIMULATION_REQUIRED");
        var distribution = request.simulation().distribution();
        check(distribution.scenarios() != null && !distribution.scenarios().isEmpty() && distribution.scenarios().size() <= 256, "SCENARIOS_REQUIRED_MAX_256");
        for (var scenario : distribution.scenarios()) {
            check(scenario != null && scenario.outcomes() != null && scenario.outcomes().keySet().equals(ids), "COMPLETE_JOINT_BINDINGS_REQUIRED");
            for (var outcome : scenario.outcomes().values()) { check(outcome != null, "IMPACT_REQUIRED"); hours(outcome.additionalHours()); }
        }
    }
    private SimulationModel.Request simulationInput(Request request, Map<String, BigDecimal> routeHours) {
        var s = request.simulation(); List<SimulationModel.JointScenario> scenarios = new ArrayList<>();
        for (var row : s.distribution().scenarios()) {
            var outcomes = new TreeMap<String, SimulationModel.Outcome>();
            for (var a : request.alternatives()) {
                var impact = row.outcomes().get(a.id());
                var duration = routeHours.get(a.routeId()).add(a.nonSailingHours()).add(impact.additionalHours());
                outcomes.put(a.id(), new SimulationModel.Outcome(impact.lossScenarioId(), duration));
            }
            scenarios.add(new SimulationModel.JointScenario(row.id(), row.probability(), outcomes));
        }
        var d = s.distribution();
        return new SimulationModel.Request(s.expectedModelVersion(), s.acknowledgeUncalibratedProbabilities(), s.seed(), s.samples(), s.tailAlpha(), s.confidenceLevel(),
                request.alternatives().stream().map(a -> new SimulationModel.Alternative(a.id(), a.contract())).toList(),
                new SimulationModel.Distribution(d.version(), d.evidenceReference(), d.dependenceDescription(), d.applicability(), scenarios), s.objectives());
    }
    private JsonNode changes(JsonNode previous, JsonNode next) {
        var result = PlanJson.MAPPER.createObjectNode(); var fields = result.putArray("changedInputSections");
        for (String key : List.of("routes", "alternatives", "simulation"))
            if (!PlanJson.sha(previous.path("declaredInputs").path(key)).equals(PlanJson.sha(next.path("declaredInputs").path(key)))) fields.add(key);
        result.put("previousInputSha256", previous.path("inputSha256").asText()).put("nextInputSha256", next.path("inputSha256").asText());
        result.set("previousFrontier", previous.path("comparison").path("paretoAlternativeIds"));
        result.set("nextFrontier", next.path("comparison").path("paretoAlternativeIds"));
        var routes = result.putObject("physicalRoutes");
        Set<String> ids = new TreeSet<>(); previous.path("routes").fieldNames().forEachRemaining(ids::add); next.path("routes").fieldNames().forEachRemaining(ids::add);
        for (String id : ids) {
            var before = previous.path("routes").path(id); var after = next.path("routes").path(id);
            var row = routes.putObject(id).put("previousRouteSha256", before.isMissingNode() ? null : PlanJson.sha(before))
                    .put("nextRouteSha256", after.isMissingNode() ? null : PlanJson.sha(after));
            row.set("previousArrival", before.path("route").path("arrival")); row.set("nextArrival", after.path("route").path("arrival"));
            if (!before.isMissingNode() && !after.isMissingNode()) row.put("distanceChangeM",
                    after.path("route").path("distanceM").decimalValue().subtract(before.path("route").path("distanceM").decimalValue()));
        }
        result.put("costUpdatePolicy", "Complete replacement inputs reviewed by caller; no automatic repricing or probability updates");
        return result;
    }
    private static void hours(BigDecimal value) { check(value != null && value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(87600)) <= 0
            && value.scale() >= 0 && value.scale() <= 6, "DECLARED_HOURS_REQUIRED_0_TO_87600_MAX_6_DECIMALS"); }
    private static void text(String value, String code) { check(value != null && !value.isBlank() && value.length() <= 2048, code); }
    private static void check(boolean value, String code) { if (!value) throw new IllegalArgumentException(code); }
}
