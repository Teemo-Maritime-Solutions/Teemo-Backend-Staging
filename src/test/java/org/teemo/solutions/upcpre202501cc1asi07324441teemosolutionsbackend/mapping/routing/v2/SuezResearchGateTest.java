package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SuezResearchGateTest {
    private PhysicalGraph graph() throws Exception {
        var original = SyntheticGraphFixture.graph();
        var edges = original.edges().stream().map(e -> new PhysicalGraph.Edge(e.id(), e.from(), e.to(), e.distanceM(), e.geometry(),
                null, "CANAL_CENTERLINE_RESEARCH", "SUEZ_CANAL_RESEARCH", Set.of(), List.of("fixture"), "UNKNOWN")).toList();
        ObjectNode manifest = original.manifest().deepCopy();
        manifest.putObject("suezControls").put("policy", "OSM_EXACT_SUEZ_CENTERLINE_GSHHG_OUTSIDE_V1").put("addedDirectedEdges", edges.size());
        manifest.putObject("tables").put("suez-evidence.jsonl", "b".repeat(64));
        return new PhysicalGraph(original.nodes(), edges, List.copyOf(original.ports().values()), manifest);
    }
    private ObjectNode report(PhysicalGraph graph) {
        var report = new ObjectMapper().createObjectNode().put("suezControlCheck", "PASS")
                .put("suezEvidenceSha256", "b".repeat(64)).put("allAddedNodesResearchPolicyReachable", true).put("sourceGeometryConflicts", 0);
        report.set("suezControls", graph.manifest().path("suezControls").deepCopy());
        return report;
    }
    @Test void canalRequiresItsOwnMatchingEvidence() throws Exception {
        var graph = graph();
        GraphCatalog.validateSuezEvidence(graph, report(graph));
        for (String field : List.of("suezControlCheck", "suezEvidenceSha256", "allAddedNodesResearchPolicyReachable", "sourceGeometryConflicts", "suezControls")) {
            var bad = report(graph); bad.remove(field);
            assertThatThrownBy(() -> GraphCatalog.validateSuezEvidence(graph, bad)).isInstanceOf(java.io.IOException.class);
        }
    }
    @Test void researchCanalIsExcludableAndDoesNotInventAnEta() throws Exception {
        var edge = graph().edges().get(0);
        assertThat(RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null,null,Set.of(),Set.of("SUEZ_CANAL_RESEARCH"),false)).evaluate(edge).allowed()).isFalse();
        assertThat(RouteCostPolicy.distance(new RouteCostPolicy.Constraints(null,null,Set.of(),Set.of(),true)).evaluate(edge).reason()).isEqualTo("LEGAL_STATUS_UNKNOWN");
        var catalog = mock(GraphCatalog.class); when(catalog.current()).thenReturn(Optional.of(graph()));
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(catalog)).build();
        mvc.perform(post("/api/v2/maritime/routes").contentType("application/json").content("""
                {"originPortId":"SYNTHETIC:0","destinationPortId":"SYNTHETIC:3","acknowledgeResearchLimitations":true,"userSuppliedSpeedKnots":14,"k":1,"maxCandidates":1,"minimumSeparationM":0}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.routes[0].estimatedHours").isEmpty())
                .andExpect(jsonPath("$.routes[0].coverage.spans[0].scope").value("SOURCED_CANAL_RESEARCH"))
                .andExpect(jsonPath("$.routes[0].explanation[3]").value(org.hamcrest.Matchers.containsString("SUEZ_CANAL_RESEARCH")));
    }
}
