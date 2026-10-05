package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.ScenarioSimulationTest.*;

class SimulationControllerTest {
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SimulationController(ENGINE)).build();
    @Test void httpExposesReferenceMetricsFrontierAndExplicitUncalibratedStatus() throws Exception {
        mvc.perform(get("/api/v2/incoterms/simulations/model")).andExpect(status().isOk())
                .andExpect(jsonPath("$.maximumSamples").value(200000));
        mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(example())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidenceStatus").value("USER_DECLARED_UNCALIBRATED"))
                .andExpect(jsonPath("$.paretoAlternativeIds.length()").value(2))
                .andExpect(jsonPath("$.alternatives[1].metrics.BUYER_LOSS.finiteDistributionReference.conditionalValueAtRisk").value(5150));
    }
    @Test void versionAndMissingDataHaveExplicitErrors() throws Exception {
        String json = JSON.writeValueAsString(example());
        mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace(ScenarioSimulation.VERSION, "old"))).andExpect(status().isConflict());
        var missing = change(j -> ((com.fasterxml.jackson.databind.node.ObjectNode) j.path("alternatives").get(0).path("contract").path("fees").get(0)).putNull("amount"));
        mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(missing))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("INCOMPLETE_CONTRACT_COSTS_OR_CARGO"));
    }
    @Test void invalidJsonAndFractionalIntegerParametersAreRejected() throws Exception {
        String json = JSON.writeValueAsString(example());
        for (String invalid : new String[] {"null", "{}", json + "{}", json.replace("\"samples\":20000", "\"samples\":20000.5"),
                json.replace("\"samples\":20000", "\"samples\":\"20000\""),
                json.replace("\"seed\":20260922", "\"seed\":1.5"),
                json.replace("\"tailAlpha\":0.95", "\"tailAlpha\":0.95,\"tailAlpha\":0.99"),
                json.replace("\"applicability\":", "\"calibrated\":true,\"applicability\":"),
                json.replace("\"term\":\"CIF\"", "\"term\":2"),
                json.replace("\"acknowledgeUncalibratedProbabilities\":true", "\"acknowledgeUncalibratedProbabilities\":false")})
            mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
    }
    @Test void oversizedRequestIsRejected() throws Exception {
        mvc.perform(post("/api/v2/incoterms/simulations").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(2097153)))
                .andExpect(status().isPayloadTooLarge());
    }
    @Test void busyPermitRejectsConcurrencyAndIsReleased() throws Exception {
        var engine = mock(ScenarioSimulation.class); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var result = ENGINE.evaluate(example());
        when(engine.evaluate(any())).thenAnswer(invocation -> { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); return result; });
        var controller = new SimulationController(engine); var pool = Executors.newSingleThreadExecutor();
        String json = JSON.writeValueAsString(example());
        try {
            var future = pool.submit(() -> controller.simulate(json));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(controller.simulate(json).getStatusCode().value()).isEqualTo(429);
            release.countDown(); assertThat(future.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.simulate("invalid").getStatusCode().value()).isEqualTo(400);
            assertThat(controller.simulate(json).getStatusCode().value()).isEqualTo(200);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
