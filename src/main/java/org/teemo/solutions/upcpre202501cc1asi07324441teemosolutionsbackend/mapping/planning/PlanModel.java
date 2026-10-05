package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather.WeatherRoutingController;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractModel;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.SimulationModel;

public final class PlanModel {
    private PlanModel() { }
    public record RouteInput(String id, WeatherRoutingController.Request request) { }
    public record Alternative(String id, String routeId, String carriageLegId, String mappingEvidence,
                              BigDecimal nonSailingHours, ContractModel.Request contract) { }
    public record Impact(String lossScenarioId, BigDecimal additionalHours) { }
    public record Scenario(String id, BigDecimal probability, Map<String, Impact> outcomes) { }
    public record Distribution(String version, String evidenceReference, String dependenceDescription,
                               String applicability, List<Scenario> scenarios) { }
    public record Simulation(String expectedModelVersion, Boolean acknowledgeUncalibratedProbabilities,
                             Long seed, Integer samples, BigDecimal tailAlpha, BigDecimal confidenceLevel,
                             Distribution distribution, List<SimulationModel.Objective> objectives) { }
    public record Request(String expectedPlanVersion, Boolean acknowledgeDeclaredEventMapping,
                          List<RouteInput> routes, List<Alternative> alternatives, Simulation simulation) { }
    public record Recalculation(String expectedParentContentSha256, String commercialReviewEvidence, Request replacement) { }
}
