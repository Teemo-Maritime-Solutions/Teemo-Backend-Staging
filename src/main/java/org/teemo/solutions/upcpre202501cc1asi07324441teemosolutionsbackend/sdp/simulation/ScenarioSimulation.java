package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.SimulationModel.*;

@Service
public final class ScenarioSimulation {
    public static final String VERSION = "JOINT_DISCRETE_RESEARCH_1";
    private static final int PROBABILITY_UNITS = 1_000_000_000;
    private static final ObjectMapper CANONICAL = JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    private final ContractEvaluator contracts;
    public ScenarioSimulation(ContractEvaluator contracts) { this.contracts = contracts; }

    public Result evaluate(Request raw) {
        require(raw != null, "REQUEST_REQUIRED");
        text(raw.expectedModelVersion(), "MODEL_VERSION_REQUIRED");
        require(VERSION.equals(raw.expectedModelVersion()), "MODEL_VERSION_MISMATCH");
        require(Boolean.TRUE.equals(raw.acknowledgeUncalibratedProbabilities()), "UNCALIBRATED_PROBABILITIES_ACK_REQUIRED");
        require(raw.seed() != null, "EXPLICIT_SEED_REQUIRED");
        require(raw.samples() != null && raw.samples() >= 100 && raw.samples() <= 200000, "SAMPLES_MUST_BE_100_TO_200000");
        bounded(raw.tailAlpha(), "0.5", "0.9999", 6, "INVALID_TAIL_ALPHA");
        bounded(raw.confidenceLevel(), "0.8", "0.9999", 6, "INVALID_CONFIDENCE_LEVEL");
        require(raw.alternatives() != null && !raw.alternatives().isEmpty() && raw.alternatives().size() <= 16, "ALTERNATIVES_REQUIRED_MAX_16");
        var alternatives = new TreeMap<String, Alternative>(); var contractResults = new TreeMap<String, ContractModel.Result>();
        String currency = null, cargoDescription = null; BigDecimal cargoValue = null;
        for (var a : raw.alternatives()) {
            require(a != null, "INVALID_ALTERNATIVE"); text(a.id(), "ALTERNATIVE_ID_REQUIRED");
            require(alternatives.put(a.id(), a) == null, "DUPLICATE_ALTERNATIVE_ID");
            var result = contracts.evaluate(a.contract());
            require(result.buyerCosts().total() != null && result.sellerCosts().total() != null
                    && a.contract().cargo().saleValue() != null, "INCOMPLETE_CONTRACT_COSTS_OR_CARGO");
            if (currency == null) {
                currency = result.currency(); cargoValue = a.contract().cargo().saleValue(); cargoDescription = a.contract().cargo().description();
            } else require(currency.equals(result.currency()) && cargoValue.compareTo(a.contract().cargo().saleValue()) == 0
                    && cargoDescription.equals(a.contract().cargo().description()), "ALTERNATIVES_REQUIRE_SAME_CURRENCY_AND_CARGO");
            contractResults.put(a.id(), result);
        }
        var distribution = raw.distribution(); require(distribution != null, "DISTRIBUTION_REQUIRED");
        text(distribution.version(), "DISTRIBUTION_VERSION_REQUIRED"); text(distribution.evidenceReference(), "DISTRIBUTION_EVIDENCE_REQUIRED");
        text(distribution.dependenceDescription(), "DEPENDENCE_DESCRIPTION_REQUIRED"); text(distribution.applicability(), "APPLICABILITY_REQUIRED");
        require(distribution.scenarios() != null && !distribution.scenarios().isEmpty() && distribution.scenarios().size() <= 256,
                "JOINT_SCENARIOS_REQUIRED_MAX_256");
        var rows = new TreeMap<String, JointScenario>(); BigDecimal probabilitySum = BigDecimal.ZERO;
        for (var row : distribution.scenarios()) {
            require(row != null, "INVALID_SCENARIO"); text(row.id(), "SCENARIO_ID_REQUIRED");
            bounded(row.probability(), "0.000000001", "1", 9, "INVALID_PROBABILITY_MAX_9_DECIMALS");
            require(row.outcomes() != null && row.outcomes().keySet().equals(alternatives.keySet()), "COMPLETE_JOINT_OUTCOMES_REQUIRED");
            for (var outcome : row.outcomes().values()) {
                require(outcome != null, "OUTCOME_REQUIRED"); text(outcome.lossScenarioId(), "LOSS_SCENARIO_ID_REQUIRED");
                bounded(outcome.durationHours(), "0", "87600", 6, "INVALID_DURATION_HOURS");
            }
            require(rows.put(row.id(), new JointScenario(row.id(), row.probability(), new TreeMap<>(row.outcomes()))) == null,
                    "DUPLICATE_JOINT_SCENARIO_ID");
            probabilitySum = probabilitySum.add(row.probability());
        }
        require(probabilitySum.compareTo(BigDecimal.ONE) == 0, "PROBABILITIES_MUST_SUM_EXACTLY_TO_ONE");
        require(raw.objectives() != null && !raw.objectives().isEmpty() && raw.objectives().size() <= 15, "EXPLICIT_OBJECTIVES_REQUIRED_MAX_15");
        var uniqueObjectives = new HashSet<Objective>();
        for (var o : raw.objectives()) require(o != null && o.dimension() != null && o.statistic() != null
                && uniqueObjectives.add(o), "INVALID_OR_DUPLICATE_OBJECTIVE");
        var scenarios = List.copyOf(rows.values());
        var normalized = new Request(raw.expectedModelVersion(), true, raw.seed(), raw.samples(), raw.tailAlpha(), raw.confidenceLevel(),
                List.copyOf(alternatives.values()), new Distribution(distribution.version(), distribution.evidenceReference(),
                distribution.dependenceDescription(), distribution.applicability(), scenarios), List.copyOf(raw.objectives()));
        int[] cumulative = new int[scenarios.size()], counts = new int[scenarios.size()]; int total = 0;
        for (int i = 0; i < scenarios.size(); i++) {
            total += scenarios.get(i).probability().movePointRight(9).intValueExact(); cumulative[i] = total;
        }
        // One integer draw selects the entire joint row; no independent marginal draws.
        var random = new Random(raw.seed());
        for (int draw = 0; draw < raw.samples(); draw++) {
            int ticket = random.nextInt(PROBABILITY_UNITS);
            int low = 0, high = cumulative.length - 1;
            while (low < high) { int mid = (low + high) >>> 1; if (ticket < cumulative[mid]) high = mid; else low = mid + 1; }
            counts[low]++;
        }
        var frequencies = new TreeMap<String, Integer>();
        for (int i = 0; i < scenarios.size(); i++) frequencies.put(scenarios.get(i).id(), counts[i]);
        int marginalCount = alternatives.size() * Dimension.values().length;
        double eps = StrictMath.sqrt(StrictMath.log(2.0 * marginalCount / (1 - raw.confidenceLevel().doubleValue())) / (2.0 * raw.samples()));
        List<AlternativeResult> results = new ArrayList<>(); List<Correlation> correlations = new ArrayList<>();
        for (var a : alternatives.values()) {
            var contract = contractResults.get(a.id()); var losses = new HashMap<String, ContractModel.ScenarioResult>();
            for (var loss : contract.conditionalLosses()) losses.put(loss.id(), loss);
            var values = new EnumMap<Dimension, List<BigDecimal>>(Dimension.class);
            for (var dim : Dimension.values()) values.put(dim, new ArrayList<>());
            for (var row : scenarios) {
                var outcome = row.outcomes().get(a.id()); var loss = losses.get(outcome.lossScenarioId());
                require(loss != null && loss.buyerGrossLoss() != null && loss.sellerGrossLoss() != null, "UNKNOWN_OR_INCOMPLETE_LOSS_SCENARIO");
                values.get(Dimension.BUYER_LOSS).add(loss.buyerGrossLoss()); values.get(Dimension.SELLER_LOSS).add(loss.sellerGrossLoss());
                values.get(Dimension.BUYER_TOTAL_COST).add(contract.buyerCosts().total().add(loss.buyerGrossLoss()));
                values.get(Dimension.SELLER_TOTAL_COST).add(contract.sellerCosts().total().add(loss.sellerGrossLoss()));
                values.get(Dimension.DURATION_HOURS).add(outcome.durationHours());
            }
            var metrics = new EnumMap<Dimension, Metric>(Dimension.class);
            for (var dim : Dimension.values()) {
                List<DiscreteRisk.Atom> reference = new ArrayList<>(), sample = new ArrayList<>();
                for (int i = 0; i < scenarios.size(); i++) {
                    reference.add(new DiscreteRisk.Atom(values.get(dim).get(i), scenarios.get(i).probability()));
                    if (counts[i] > 0) sample.add(new DiscreteRisk.Atom(values.get(dim).get(i), BigDecimal.valueOf(counts[i])));
                }
                metrics.put(dim, DiscreteRisk.metric(reference, sample, raw.tailAlpha(), eps));
            }
            for (var first : Dimension.values()) for (var second : Dimension.values()) if (first.ordinal() < second.ordinal())
                correlations.add(correlation(a.id(), first, second, values, scenarios, metrics));
            var vector = raw.objectives().stream().map(o -> DiscreteRisk.select(metrics.get(o.dimension()).finiteDistributionReference(), o.statistic())).toList();
            results.add(new AlternativeResult(a.id(), contract.buyerCosts().total(), contract.sellerCosts().total(), metrics, vector, List.of()));
        }
        var withDominators = new ArrayList<AlternativeResult>(); var frontier = new ArrayList<String>();
        for (var candidate : results) {
            var dominators = results.stream().filter(other -> dominates(other.objectiveVector(), candidate.objectiveVector())).map(AlternativeResult::id).toList();
            if (dominators.isEmpty()) frontier.add(candidate.id());
            withDominators.add(new AlternativeResult(candidate.id(), candidate.buyerBaseCost(), candidate.sellerBaseCost(),
                    candidate.metrics(), candidate.objectiveVector(), dominators));
        }
        return new Result(VERSION, digest(normalized), "USER_DECLARED_UNCALIBRATED", currency, normalized,
                "JAVA_UTIL_RANDOM_LCG48_NEXTINT_1000000000", "CANONICAL_ID_ORDER_COMMON_JOINT_ROW_DRAWS",
                BigDecimal.ONE.subtract(raw.tailAlpha()), BigDecimal.ONE.subtract(raw.tailAlpha()).multiply(BigDecimal.valueOf(raw.samples())),
                eps, frequencies, List.copyOf(withDominators), List.copyOf(frontier), List.copyOf(correlations), List.of(
                "Probabilities, joint dependence, applicability and durations are declared, not empirically calibrated",
                "All metrics and Pareto comparisons are conditional on the supplied finite distribution and contractual model",
                "Sampling intervals use a simultaneous DKW union bound for Monte Carlo error only, under ideal IID sampling",
                "A pseudorandom seeded generator approximates IID sampling; confidence is not a guarantee for a fixed seed or real voyages",
                "VaR is the lower alpha quantile; CVaR integrates exactly the upper 1-alpha mass with fractional threshold atoms",
                "Finite-distribution arithmetic uses DECIMAL128 for division; correlations and bounds use binary floating point",
                "Costs are logistics base amounts plus gross conditional losses; no insurance recovery, cargo purchase price or unlisted fees",
                "Alternative outcomes share joint rows; alternatives and mutually exclusive rows are never added together",
                "Pareto minimizes only explicitly selected reference metrics, retains ties and does not establish route control or feasibility",
                "No recalculation of physical routes, weather probabilities, calibrated fuel consumption or emissions"));
    }
    static boolean dominates(List<BigDecimal> a, List<BigDecimal> b) {
        boolean strict = false;
        for (int i = 0; i < a.size(); i++) { int c = a.get(i).compareTo(b.get(i)); if (c > 0) return false; if (c < 0) strict = true; }
        return strict;
    }
    private static Correlation correlation(String id, Dimension x, Dimension y, Map<Dimension, List<BigDecimal>> values,
                                          List<JointScenario> rows, Map<Dimension, Metric> metrics) {
        BigDecimal mx = metrics.get(x).finiteDistributionReference().mean(), my = metrics.get(y).finiteDistributionReference().mean();
        double covariance = 0, vx = 0, vy = 0;
        for (int i = 0; i < rows.size(); i++) {
            double dx = values.get(x).get(i).subtract(mx).doubleValue(), dy = values.get(y).get(i).subtract(my).doubleValue();
            double p = rows.get(i).probability().doubleValue(); covariance += p * dx * dy; vx += p * dx * dx; vy += p * dy * dy;
        }
        return vx == 0 || vy == 0 ? new Correlation(id, x, y, null, "UNDEFINED_CONSTANT_MARGINAL")
                : new Correlation(id, x, y, Math.max(-1, Math.min(1, covariance / StrictMath.sqrt(vx * vy))), "DECLARED_FINITE_DISTRIBUTION");
    }
    private static String digest(Request request) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CANONICAL.writeValueAsBytes(request))); }
        catch (Exception unexpected) { throw new IllegalStateException("Cannot hash normalized request", unexpected); }
    }
    private static void text(String value, String code) { require(value != null && !value.isBlank() && value.length() <= 2048, code); }
    private static void bounded(BigDecimal value, String low, String high, int scale, String code) {
        require(value != null && value.scale() >= -6 && value.scale() <= scale && value.precision() <= 24
                && value.compareTo(new BigDecimal(low)) >= 0 && value.compareTo(new BigDecimal(high)) <= 0, code);
    }
    private static void require(boolean valid, String code) { if (!valid) throw new IllegalArgumentException(code); }
}
