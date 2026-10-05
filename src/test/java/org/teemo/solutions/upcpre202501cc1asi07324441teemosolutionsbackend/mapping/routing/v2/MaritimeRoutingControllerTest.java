package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaritimeRoutingControllerTest {
    @Test void missingArtifactIsExplicitUnavailable() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(new GraphCatalog("", 1000000))).build();
        mvc.perform(get("/api/v2/maritime/graph")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("ARTIFACT_NOT_CONFIGURED"));
    }
    @Test void researchRoutesRequireAcknowledgementAndHaveNoInventedTime() throws Exception {
        var catalog = mock(GraphCatalog.class); when(catalog.current()).thenReturn(Optional.of(SyntheticGraphFixture.graph()));
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(catalog)).build();
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originPortId\":\"SYNTHETIC:0\",\"destinationPortId\":\"SYNTHETIC:3\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value("OPERATIONAL_VALIDATION_UNAVAILABLE"));
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originPortId\":\"SYNTHETIC:0\",\"destinationPortId\":\"SYNTHETIC:3\",\"acknowledgeResearchLimitations\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESEARCH_ONLY"))
                .andExpect(jsonPath("$.routes[0].estimatedHours").isEmpty())
                .andExpect(jsonPath("$.routes[0].coverage.purpose").value("RESEARCH_NOT_NAVIGATION"))
                .andExpect(jsonPath("$.routes[0].coverage.spans[0].scope").value("GLOBAL_COASTLINE_RESEARCH"))
                .andExpect(jsonPath("$.routes[0].geometryType").value("MultiLineString"));
    }

    @Test void regionalRestrictionsRemainHardForEveryAlternativeAndCoverageIsPerRoute() throws Exception {
        var base = SyntheticGraphFixture.graph();
        var edges = base.edges().stream().map(e -> new PhysicalGraph.Edge(e.id(), e.from(), e.to(), e.distanceM(),
                e.geometry(), e.minimumDepthM(), e.kind(), e.specialZone(), java.util.Set.of("SOURCE_PERMISSION_PENDING"),
                e.sourceIds(), e.legalStatus(), java.util.List.of("NOAA_NEW_YORK_V2"))).toList();
        var graph = new PhysicalGraph(base.nodes(), edges, java.util.List.copyOf(base.ports().values()), base.manifest());
        var catalog = mock(GraphCatalog.class); when(catalog.current()).thenReturn(Optional.of(graph));
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(catalog)).build();
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originPortId\":\"SYNTHETIC:0\",\"destinationPortId\":\"SYNTHETIC:3\",\"acknowledgeResearchLimitations\":true,\"k\":3}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.rejectedEdgeEvaluations.UNRESOLVED_HARD_RESTRICTION").exists())
                .andExpect(jsonPath("$.routes").isEmpty());
    }
    @Test void draftAndMissingClearanceAreNotSilentlyAssumed() throws Exception {
        var catalog = mock(GraphCatalog.class); when(catalog.current()).thenReturn(Optional.of(SyntheticGraphFixture.graph()));
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(catalog)).build();
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"originPortId\":\"SYNTHETIC:0\",\"destinationPortId\":\"SYNTHETIC:3\",\"acknowledgeResearchLimitations\":true,\"userSuppliedDraftM\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v2/maritime/ports?limit=100000")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"weather\":\"must-not-be-silently-ignored\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void queriesExposeQualityAndStaleGraphConstraintsAreRejected() throws Exception {
        var graph = SyntheticGraphFixture.graph();
        var catalog = mock(GraphCatalog.class); when(catalog.current()).thenReturn(Optional.of(graph));
        var mvc = MockMvcBuilders.standaloneSetup(new MaritimeRoutingController(catalog)).build();
        mvc.perform(get("/api/v2/maritime/ports"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confidence").value("NOT_CALIBRATED_NOT_NAVIGATIONAL"));
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originPortId\":\"SYNTHETIC:0\",\"destinationPortId\":\"SYNTHETIC:3\",\"acknowledgeResearchLimitations\":true,\"expectedGraphVersion\":\"stale\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value("GRAPH_VERSION_MISMATCH"));
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originPortId\":\"SYNTHETIC:0\",\"destinationPortId\":\"SYNTHETIC:3\",\"acknowledgeResearchLimitations\":true,\"userSuppliedClosedEdgeIds\":[0]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("GRAPH_VERSION_REQUIRED_FOR_EDGE_CONSTRAINTS"));
        mvc.perform(post("/api/v2/maritime/routes").contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.sources").isArray());
    }
}
