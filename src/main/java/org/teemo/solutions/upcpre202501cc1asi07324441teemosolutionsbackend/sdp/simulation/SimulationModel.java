package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractModel;

public final class SimulationModel {
    private SimulationModel() { }
    public enum Dimension { BUYER_LOSS, SELLER_LOSS, BUYER_TOTAL_COST, SELLER_TOTAL_COST, DURATION_HOURS }
    public enum Statistic { MEAN, VAR, CVAR }
    public record Objective(Dimension dimension, Statistic statistic) { }
    public record Alternative(String id, ContractModel.Request contract) { }
    public record Outcome(String lossScenarioId, BigDecimal durationHours) { }
    public record JointScenario(String id, BigDecimal probability, Map<String, Outcome> outcomes) { }
    public record Distribution(String version, String evidenceReference, String dependenceDescription,
                               String applicability, List<JointScenario> scenarios) { }
    public record Request(String expectedModelVersion, Boolean acknowledgeUncalibratedProbabilities,
                          Long seed, Integer samples, BigDecimal tailAlpha, BigDecimal confidenceLevel,
                          List<Alternative> alternatives, Distribution distribution, List<Objective> objectives) { }
    public record Summary(BigDecimal mean, BigDecimal valueAtRisk, BigDecimal conditionalValueAtRisk) { }
    public record Interval(BigDecimal lower, BigDecimal upper) { }
    public record SamplingBounds(Interval mean, Interval valueAtRisk, Interval conditionalValueAtRisk) { }
    public record Metric(Summary finiteDistributionReference, Summary monteCarlo, SamplingBounds samplingBounds,
                         BigDecimal supportMinimum, BigDecimal supportMaximum) { }
    public record AlternativeResult(String id, BigDecimal buyerBaseCost, BigDecimal sellerBaseCost,
                                    Map<Dimension, Metric> metrics, List<BigDecimal> objectiveVector,
                                    List<String> dominatedBy) { }
    public record Correlation(String alternativeId, Dimension first, Dimension second, Double pearson,
                              String status) { }
    public record Result(String modelVersion, String inputSha256, String evidenceStatus, String currency,
                         Request declaredInputs, String randomGenerator, String samplingConvention,
                         BigDecimal tailProbability, BigDecimal nominalSampleTailCount,
                         double simultaneousCdfErrorBound, Map<String, Integer> sampledScenarioCounts,
                         List<AlternativeResult> alternatives, List<String> paretoAlternativeIds,
                         List<Correlation> finiteDistributionCorrelations, List<String> limitations) { }
}
