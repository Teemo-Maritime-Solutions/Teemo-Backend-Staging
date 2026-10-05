package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RouteSearchTest {
    private RouteCostPolicy distance() { return RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(), Set.of(), false)); }

    @Test void directedSearchDoesNotInventReverseEdges() throws Exception {
        var graph = SyntheticGraphFixture.graph();
        assertThat(new RouteSearch(graph, distance(), 100).shortest(0, 3, Set.of(), Set.of())).isPresent();
        assertThat(new RouteSearch(graph, distance(), 100).shortest(3, 0, Set.of(), Set.of())).isEmpty();
    }
    @Test void yenReturnsThreeUniqueLooplessSortedPaths() throws Exception {
        var result = new RouteSearch(SyntheticGraphFixture.graph(), distance(), 1000).alternatives(0, 3, 3, 10, 0);
        assertThat(result.paths()).hasSize(3);
        assertThat(result.paths().stream().map(RouteSearch.Path::signature).distinct()).hasSize(3);
        assertThat(result.paths().stream().map(RouteSearch.Path::cost).toList()).isSorted();
        result.paths().forEach(p -> assertThat(p.nodes()).doesNotHaveDuplicates());
    }
    @Test void geographyRejectsNearbyVariantsButAcceptsWideDetour() throws Exception {
        var result = new RouteSearch(SyntheticGraphFixture.graph(), distance(), 1000).alternatives(0, 3, 3, 10, 100000);
        assertThat(result.paths()).hasSize(2);
        assertThat(result.rejectedForSimilarity()).isEqualTo(1);
    }
    @Test void unknownDepthFailsClosedWhenDraftRequested() throws Exception {
        var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(10.0, 1.0, Set.of(), Set.of(), false));
        var result = new RouteSearch(SyntheticGraphFixture.graph(), policy, 100).alternatives(0, 3, 1, 1, 0);
        assertThat(result.paths()).isEmpty();
        assertThat(result.rejectedEvaluations()).containsKey("DEPTH_UNAVAILABLE");
    }
    @Test void geographicAndYenCandidatesShareOneReportedBudget() throws Exception {
        var result = new RouteSearch(SyntheticGraphFixture.graph(), distance(), 1000).alternatives(0, 3, 3, 3, 100000);
        assertThat(result.candidates()).isLessThanOrEqualTo(3);
        assertThat(result.paths().size()).isLessThanOrEqualTo(result.candidates());
    }
    @Test void closuresApplyDuringSearchAndDoNotMutateGraph() throws Exception {
        var graph = SyntheticGraphFixture.graph();
        var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, Set.of(0, 2), Set.of(), false));
        var result = new RouteSearch(graph, policy, 100).shortest(0, 3, Set.of(), Set.of()).orElseThrow();
        assertThat(result.nodes()).containsExactly(0, 4, 3);
        assertThat(graph.edges()).hasSize(6);
    }
    @Test void contextualPolicyChangesRouteAndDefaultsToAdmissibleZeroBound() throws Exception {
        var graph = SyntheticGraphFixture.graph();
        RouteCostPolicy policy = edge -> new RouteCostPolicy.Evaluation(true, edge.distanceM() * (edge.to() == 1 ? 10 : 1), "SYNTHETIC_POLICY");
        assertThat(new RouteSearch(graph, policy, 100).shortest(0, 3, Set.of(), Set.of()).orElseThrow().nodes()).containsExactly(0, 2, 3);
    }
    @Test void budgetExhaustionIsNotReportedAsDisconnected() throws Exception {
        var result = new RouteSearch(SyntheticGraphFixture.graph(), distance(), 1).alternatives(0, 3, 1, 1, 0);
        assertThat(result.paths()).isEmpty(); assertThat(result.budgetExhausted()).isTrue();
    }
    @Test void aStarMatchesDijkstraAcrossSyntheticClosures() throws Exception {
        var graph = SyntheticGraphFixture.graph();
        for (int mask = 0; mask < 64; mask++) {
            Set<Integer> closed = new HashSet<>();
            for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) closed.add(i);
            var policy = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null, null, closed, Set.of(), false));
            RouteCostPolicy zeroBound = policy::evaluate;
            var a = new RouteSearch(graph, policy, 100).shortest(0, 3, Set.of(), Set.of());
            var d = new RouteSearch(graph, zeroBound, 100).shortest(0, 3, Set.of(), Set.of());
            assertThat(a.map(RouteSearch.Path::cost)).isEqualTo(d.map(RouteSearch.Path::cost));
        }
    }
    @Test void negativeCostsAndImplicitClearanceAreRejected() {
        assertThatThrownBy(() -> new RouteCostPolicy.Evaluation(true, -1, "invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteCostPolicy.Constraints(10.0, null, Set.of(), Set.of(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RouteCostPolicy.Constraints(null, null, new HashSet<>(Arrays.asList(1, null)), Set.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptedMeasurementRoundingCannotMakeGeodesicHeuristicOverestimate() throws Exception {
        var original = SyntheticGraphFixture.graph();
        var rounded = original.edges().stream().map(e -> new PhysicalGraph.Edge(e.id(), e.from(), e.to(),
                e.distanceM() * (1 - 5e-9), e.geometry(), e.minimumDepthM(), e.kind(), e.specialZone(),
                e.restrictions(), e.sourceIds(), e.legalStatus())).toList();
        var graph = new PhysicalGraph(original.nodes(), rounded, List.of(), original.manifest());
        assertThat(graph.distanceLowerBoundFactor()).isLessThan(1);
        var expected = new RouteSearch(graph, distance()::evaluate, 100).shortest(0, 3, Set.of(), Set.of()).orElseThrow();
        var actual = new RouteSearch(graph, distance(), 100).shortest(0, 3, Set.of(), Set.of()).orElseThrow();
        assertThat(actual.cost()).isEqualTo(expected.cost());
    }

    @Test void coincidentMultiResolutionCentresDoNotInvalidateGeodesicBound() throws Exception {
        var original = SyntheticGraphFixture.graph();
        var point = original.nodes().get(0).point();
        var alias = new PhysicalGraph.Node("SYNTHETIC:ALIAS", point, "SYNTHETIC", List.of("fixture"));
        var transfer = new PhysicalGraph.Edge(0, 0, 1, Math.ulp(1.0), List.of(point, point), null,
                "SYNTHETIC", null, Set.of(), List.of("fixture"), "UNKNOWN");
        var graph = new PhysicalGraph(List.of(original.nodes().get(0), alias), List.of(transfer), List.of(), original.manifest());
        assertThat(graph.distanceLowerBoundFactor()).isEqualTo(1);
        assertThat(new RouteSearch(graph, distance(), 10).shortest(0, 1, Set.of(), Set.of())).isPresent();
    }

    @Test void knownProhibitionsAreHardEvenWhenUnknownPermissionsAreAllowedForResearch() throws Exception {
        var original = SyntheticGraphFixture.graph().edges().get(0);
        var prohibited = new PhysicalGraph.Edge(original.id(), original.from(), original.to(), original.distanceM(),
                original.geometry(), null, original.kind(), null, Set.of(), original.sourceIds(), "PROHIBITED");
        assertThat(distance().evaluate(prohibited).allowed()).isFalse();
    }

    @Test void availableSyntheticDepthEnforcesDraftPlusExplicitClearance() throws Exception {
        var edge = SyntheticGraphFixture.graph().edges().get(0);
        var measured = new PhysicalGraph.Edge(edge.id(), edge.from(), edge.to(), edge.distanceM(),
                edge.geometry(), 12.0, edge.kind(), null, Set.of(), edge.sourceIds(), "UNKNOWN");
        var equal = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(10.0, 2.0, Set.of(), Set.of(), false));
        var deeper = RouteCostPolicy.distance(new RouteCostPolicy.Constraints(11.0, 2.0, Set.of(), Set.of(), false));
        assertThat(equal.evaluate(measured).allowed()).isTrue(); assertThat(deeper.evaluate(measured).allowed()).isFalse();
    }
}
