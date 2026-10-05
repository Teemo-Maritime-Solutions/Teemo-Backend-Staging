package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DiscreteRiskTest {
    static BigDecimal n(String n) { return new BigDecimal(n); }
    static DiscreteRisk.Atom atom(String value, String mass) { return new DiscreteRisk.Atom(n(value), n(mass)); }
    @Test void partialThresholdAtomIsNotNaiveConditionalAverage() {
        var r = DiscreteRisk.summarize(List.of(atom("0", ".96"), atom("100", ".04")), n(".95"));
        assertThat(r.mean()).isEqualByComparingTo("4");
        assertThat(r.valueAtRisk()).isZero();
        assertThat(r.conditionalValueAtRisk()).isEqualByComparingTo("80");
    }
    @Test void quantileAtExactJumpAndUpperTail() {
        var atoms = List.of(atom("0", ".95"), atom("100", ".05"));
        assertThat(DiscreteRisk.summarize(atoms, n(".95")).valueAtRisk()).isZero();
        assertThat(DiscreteRisk.summarize(atoms, n(".95")).conditionalValueAtRisk()).isEqualByComparingTo("100");
        assertThat(DiscreteRisk.summarize(atoms, n(".975")).valueAtRisk()).isEqualByComparingTo("100");
    }
    @Test void empiricalCountsAllowFractionalTailSize() {
        var r = DiscreteRisk.summarize(List.of(atom("0", "3"), atom("100", "1")), n(".6"));
        assertThat(r.mean()).isEqualByComparingTo("25");
        assertThat(r.conditionalValueAtRisk()).isEqualByComparingTo("62.5");
    }
    @Test void unseenTailRemainsInsideSupportBasedConfidenceBounds() {
        var r = DiscreteRisk.metric(List.of(atom("0", ".999"), atom("100", ".001")),
                List.of(atom("0", "100")), n(".99"), .2);
        assertThat(r.finiteDistributionReference().conditionalValueAtRisk()).isEqualByComparingTo("10");
        assertThat(r.monteCarlo().conditionalValueAtRisk()).isZero();
        assertThat(r.samplingBounds().conditionalValueAtRisk().upper()).isEqualByComparingTo("100");
        assertThat(r.samplingBounds().valueAtRisk().upper()).isEqualByComparingTo("100");
    }
    @Test void constantDistributionHasZeroSamplingWidth() {
        var r = DiscreteRisk.metric(List.of(atom("7", "1")), List.of(atom("7", "100")), n(".99"), .2);
        assertThat(r.finiteDistributionReference().mean()).isEqualByComparingTo("7");
        assertThat(r.samplingBounds().conditionalValueAtRisk().lower()).isEqualByComparingTo("7");
        assertThat(r.samplingBounds().conditionalValueAtRisk().upper()).isEqualByComparingTo("7");
    }
    @Test void paretoRetainsTiesAndTradeoffsWithoutWeights() {
        assertThat(ScenarioSimulation.dominates(List.of(n("1"), n("5")), List.of(n("2"), n("6")))).isTrue();
        assertThat(ScenarioSimulation.dominates(List.of(n("1"), n("5")), List.of(n("1.0"), n("5.0")))).isFalse();
        assertThat(ScenarioSimulation.dominates(List.of(n("1"), n("5")), List.of(n("5"), n("1")))).isFalse();
    }
}
