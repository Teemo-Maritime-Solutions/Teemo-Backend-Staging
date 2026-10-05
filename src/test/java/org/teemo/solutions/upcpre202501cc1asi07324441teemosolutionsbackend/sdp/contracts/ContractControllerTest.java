package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractEvaluatorTest.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts.ContractModel.*;

class ContractControllerTest {
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ContractController(new ContractEvaluator())).build();
    @Test void catalogueAndCifEvaluationExposeAllActorsAndPreserveNullableRecovery() throws Exception {
        mvc.perform(get("/api/v2/incoterms/rules")).andExpect(status().isOk()).andExpect(jsonPath("$.rules.length()").value(11));
        mvc.perform(post("/api/v2/incoterms/evaluate").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(fixture(Term.CIF, 6))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellerCosts.total").value(320))
                .andExpect(jsonPath("$.legs[6].transportPayer").value("SELLER"))
                .andExpect(jsonPath("$.legs[6].cargoRiskBearer").value("BUYER"))
                .andExpect(jsonPath("$.legs[6].routingControl").value("CARRIER"))
                .andExpect(jsonPath("$.conditionalLosses[0].insuranceRecovery").doesNotExist());
    }
    @Test void wrongVersionIs409AndAbsentAcknowledgementIs400() throws Exception {
        String json = JSON.writeValueAsString(fixture(Term.CIF, 6));
        mvc.perform(post("/api/v2/incoterms/evaluate").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace(IncotermRules.VERSION, "obsolete"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v2/incoterms/evaluate").contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"acknowledgeResearchLimitations\":true", "\"acknowledgeResearchLimitations\":false")))
                .andExpect(status().isBadRequest());
    }
    @Test void strictJsonRejectsTyposNestedFieldsDuplicateKeysEnumsAndCoercions() throws Exception {
        String json = JSON.writeValueAsString(fixture(Term.CIF, 6));
        for (String invalid : new String[] {"null", "{}", json + "{}", json.replace("\"currency\":\"USD\"", "\"currency\":\"USD\",\"currency\":\"EUR\""),
                json.replace("\"term\":\"CIF\"", "\"term\":7"),
                json.replace("\"saleValue\":10000.00", "\"saleValue\":\"10000.00\""),
                json.replace("\"contractReference\":", "\"typo\":1,\"contractReference\":"),
                json.replace("\"currency\":", "\"probability\":0.3,\"currency\":")}) {
            mvc.perform(post("/api/v2/incoterms/evaluate").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
    }
}
