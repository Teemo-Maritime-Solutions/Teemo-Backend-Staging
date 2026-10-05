package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractEvaluator;
import static org.assertj.core.api.Assertions.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.SimulationModel.*;

class ScenarioSimulationTest {
    static final ObjectMapper JSON = new ObjectMapper().setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    static final ScenarioSimulation ENGINE = new ScenarioSimulation(new ContractEvaluator());
    static Request example() throws Exception { return JSON.readValue(Files.readString(Path.of("docs/maritime-routing/phase6-example.json")), Request.class); }
    static Request change(java.util.function.Consumer<JsonNode> action) throws Exception {
        JsonNode tree = JSON.valueToTree(example()); action.accept(tree); return JSON.treeToValue(tree, Request.class);
    }
    static AlternativeResult alternative(Result result, String id) { return result.alternatives().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(); }
    @Test void finiteReferenceMatchesHandCalculatedCostsTailAndDuration() throws Exception {
        var result = ENGINE.evaluate(example()); var economy = alternative(result, "economy");
        var loss = economy.metrics().get(Dimension.BUYER_LOSS).finiteDistributionReference();
        assertThat(loss.mean()).isEqualByComparingTo("320");
        assertThat(loss.valueAtRisk()).isEqualByComparingTo("1250");
        assertThat(loss.conditionalValueAtRisk()).isEqualByComparingTo("5150");
        assertThat(economy.objectiveVector()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1520"), new BigDecimal("6350"), new BigDecimal("51.36"));
        assertThat(alternative(result, "protected").objectiveVector()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1780"), new BigDecimal("3050"), new BigDecimal("53.68"));
        assertThat(result.paretoAlternativeIds()).containsExactly("economy", "protected");
        assertThat(alternative(result, "dominated").dominatedBy()).containsExactly("economy");
        assertThat(result.sampledScenarioCounts().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(20000);
        assertThat(result.evidenceStatus()).isEqualTo("USER_DECLARED_UNCALIBRATED");
    }
    @Test void fixedSeedAndCanonicalOrderingGiveIdenticalResults() throws Exception {
        var baseline = ENGINE.evaluate(example()); assertThat(ENGINE.evaluate(example())).isEqualTo(baseline);
        var reordered = change(j -> {
            for (JsonNode node : List.of(j.path("alternatives"), j.path("distribution").path("scenarios"))) {
                var array = (ArrayNode) node; var list = new ArrayList<JsonNode>(); array.forEach(list::add);
                Collections.reverse(list); array.removeAll(); list.forEach(array::add);
            }
        });
        assertThat(ENGINE.evaluate(reordered)).isEqualTo(baseline);
        var otherSeed = ENGINE.evaluate(change(j -> ((ObjectNode) j).put("seed", 12345)));
        assertThat(otherSeed.sampledScenarioCounts()).isNotEqualTo(baseline.sampledScenarioCounts());
        assertThat(otherSeed.paretoAlternativeIds()).isEqualTo(baseline.paretoAlternativeIds());
    }
    @Test void commonDrawsPreserveAlternativeDependenceAndWeightedCorrelation() throws Exception {
        var result = ENGINE.evaluate(example());
        assertThat(alternative(result, "economy").metrics().get(Dimension.BUYER_LOSS))
                .isEqualTo(alternative(result, "dominated").metrics().get(Dimension.BUYER_LOSS));
        var corr = result.finiteDistributionCorrelations().stream().filter(c -> c.alternativeId().equals("economy")
                && c.first() == Dimension.BUYER_LOSS && c.second() == Dimension.BUYER_TOTAL_COST).findFirst().orElseThrow();
        assertThat(corr.pearson()).isCloseTo(1, within(1e-12));
        assertThat(result.finiteDistributionCorrelations()).anySatisfy(c -> {
            assertThat(c.first()).isEqualTo(Dimension.SELLER_LOSS); assertThat(c.pearson()).isNull();
            assertThat(c.status()).isEqualTo("UNDEFINED_CONSTANT_MARGINAL");
        });
        int delay = result.sampledScenarioCounts().get("delay"), storm = result.sampledScenarioCounts().get("storm");
        var expected = new BigDecimal(1250L * delay + 11000L * storm).divide(new BigDecimal("20000"));
        assertThat(alternative(result, "economy").metrics().get(Dimension.BUYER_LOSS).monteCarlo().mean()).isEqualByComparingTo(expected);
    }
    @Test void samplingIntervalsContainReferenceForRegisteredExample() throws Exception {
        var result = ENGINE.evaluate(example());
        for (var alternative : result.alternatives()) for (var metric : alternative.metrics().values()) {
            var ref = metric.finiteDistributionReference(); var bounds = metric.samplingBounds();
            assertThat(ref.mean()).isBetween(bounds.mean().lower(), bounds.mean().upper());
            assertThat(ref.valueAtRisk()).isBetween(bounds.valueAtRisk().lower(), bounds.valueAtRisk().upper());
            assertThat(ref.conditionalValueAtRisk()).isBetween(bounds.conditionalValueAtRisk().lower(), bounds.conditionalValueAtRisk().upper());
        }
    }
    @Test void tiedAlternativesRemainOnFrontier() throws Exception {
        var request = change(j -> {
            var alternatives = (ArrayNode) j.path("alternatives"); var clone = alternatives.get(0).deepCopy();
            ((ObjectNode) clone).put("id", "dominated"); alternatives.set(2, clone);
        });
        assertThat(ENGINE.evaluate(request).paretoAlternativeIds()).containsExactly("dominated", "economy", "protected");
    }
    @Test void rejectsInvalidProbabilitiesJointCoverageAndIncompleteCosts() throws Exception {
        List<java.util.function.Consumer<JsonNode>> edits = List.of(
                j -> ((ObjectNode) j.path("distribution").path("scenarios").get(0)).put("probability", .89),
                j -> ((ObjectNode) j.path("distribution").path("scenarios").get(0)).put("probability", -.1),
                j -> ((ObjectNode) j.path("distribution").path("scenarios").get(0)).put("probability", new BigDecimal("0.0000000001")),
                j -> ((ObjectNode) j.path("distribution").path("scenarios").get(0).path("outcomes")).remove("economy"),
                j -> ((ObjectNode) j.path("distribution").path("scenarios").get(0).path("outcomes").path("economy")).put("lossScenarioId", "missing"),
                j -> ((ObjectNode) j.path("distribution").path("scenarios").get(0).path("outcomes").path("economy")).put("durationHours", -1),
                j -> ((ObjectNode) j.path("alternatives").get(0).path("contract").path("fees").get(0)).putNull("amount"),
                j -> ((ObjectNode) j.path("alternatives").get(0).path("contract")).put("currency", "EUR"),
                j -> ((ObjectNode) j).put("samples", 200001),
                j -> ((ObjectNode) j).put("tailAlpha", 1),
                j -> ((ObjectNode) j).put("confidenceLevel", 1),
                j -> ((ObjectNode) j).putNull("seed"),
                j -> ((ObjectNode) j.path("alternatives").get(1)).put("id", "economy"),
                j -> ((ArrayNode) j.path("objectives")).add(j.path("objectives").get(0).deepCopy()));
        for (var edit : edits) { var invalid = change(edit); assertThatThrownBy(() -> ENGINE.evaluate(invalid)).isInstanceOf(IllegalArgumentException.class); }
    }
    @Test void highConfidenceWidensOnlySamplingBounds() throws Exception {
        var baseline = ENGINE.evaluate(example()); var high = ENGINE.evaluate(change(j -> ((ObjectNode) j).put("confidenceLevel", .99)));
        assertThat(high.simultaneousCdfErrorBound()).isGreaterThan(baseline.simultaneousCdfErrorBound());
        assertThat(high.sampledScenarioCounts()).isEqualTo(baseline.sampledScenarioCounts());
        assertThat(high.paretoAlternativeIds()).isEqualTo(baseline.paretoAlternativeIds());
    }
    @Test void incotermChangeExposesBuyerSellerTradeoffWithoutChoosingAnActor() throws Exception {
        var request = change(j -> {
            var alternatives = (ArrayNode) j.path("alternatives"); var cif = alternatives.get(0).deepCopy(); var dap = cif.deepCopy();
            ((ObjectNode) dap).put("id", "delivered");
            ((ObjectNode) dap.path("contract")).put("term", "DAP").put("deliveryEventId", "destination-ready");
            for (var fee : dap.path("contract").path("fees")) if (fee.path("kind").asText().equals("INSURANCE"))
                ((ObjectNode) fee).put("agreedPayer", "SELLER");
            alternatives.removeAll().add(cif).add(dap);
            for (var row : j.path("distribution").path("scenarios")) {
                var outcomes = (ObjectNode) row.path("outcomes"); var outcome = outcomes.path("economy").deepCopy();
                outcomes.removeAll(); outcomes.set("economy", outcome); outcomes.set("delivered", outcome.deepCopy());
            }
            var objectives = (ArrayNode) j.path("objectives"); objectives.removeAll();
            objectives.addObject().put("dimension", "BUYER_TOTAL_COST").put("statistic", "MEAN");
            objectives.addObject().put("dimension", "SELLER_TOTAL_COST").put("statistic", "MEAN");
        });
        var result = ENGINE.evaluate(request);
        assertThat(alternative(result, "economy").objectiveVector()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1520"), new BigDecimal("4750"));
        assertThat(alternative(result, "delivered").objectiveVector()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1240"), new BigDecimal("5030"));
        assertThat(result.paretoAlternativeIds()).containsExactly("delivered", "economy");
    }
}
