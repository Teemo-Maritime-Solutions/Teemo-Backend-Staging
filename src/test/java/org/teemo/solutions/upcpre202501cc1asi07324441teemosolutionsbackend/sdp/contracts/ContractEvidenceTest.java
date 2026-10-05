package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContractEvidenceTest {
    @Test void publishedExampleAndFrozenImplementationsProduceBoundEvidence() throws Exception {
        var mapper = new ObjectMapper();
        var mvc = MockMvcBuilders.standaloneSetup(new ContractController(new ContractEvaluator())).build();
        Path example = Path.of("docs/maritime-routing/phase5-example-cif.json");
        String body = mvc.perform(post("/api/v2/incoterms/evaluate").contentType(MediaType.APPLICATION_JSON)
                .content(Files.readString(example))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var response = mapper.readTree(body);
        assertThat(response.path("sellerCosts").path("total").decimalValue()).isEqualByComparingTo("4750");
        assertThat(response.path("buyerCosts").path("total").decimalValue()).isEqualByComparingTo("1200");
        assertThat(response.path("conditionalLosses").get(0).path("buyerGrossLoss").decimalValue()).isEqualByComparingTo("10250");
        assertThat(response.path("conditionalLosses").get(0).path("insuranceRecovery").isNull()).isTrue();
        assertThat(response.path("insurance").path("minimumInsuredAmount").decimalValue()).isEqualByComparingTo("110000");
        Map<String, String> bindings = new TreeMap<>(); bindings.put(example.toString().replace('\\', '/'), sha(example));
        int frozenCount = 0;
        for (String protocol : List.of("phase3-research-protocol-v3-20260921.json", "phase4-protocol-v3-20260922.json")) {
            Path path = Path.of("docs/maritime-routing", protocol);
            bindings.put(path.toString().replace('\\', '/'), sha(path));
            var fields = mapper.readTree(Files.readAllBytes(path)).path("implementation").fields();
            while (fields.hasNext()) {
                var field = fields.next();
                assertThat(sha(Path.of(field.getKey()))).as("Frozen implementation %s", field.getKey()).isEqualTo(field.getValue().asText());
                bindings.put(field.getKey(), field.getValue().asText()); frozenCount++;
            }
        }
        String base = "org/teemo/solutions/upcpre202501cc1asi07324441teemosolutionsbackend/sdp/contracts";
        for (String root : List.of("src/main/java/", "src/test/java/")) {
            try (var paths = Files.list(Path.of(root + base))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList())
                    bindings.put(path.toString().replace('\\', '/'), sha(path));
            }
        }
        Path out = Path.of(System.getProperty("maritime.contract.report", "target/maritime-routing/phase5"));
        Files.createDirectories(out);
        Files.writeString(out.resolve("example-response.json"), body);
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.resolve("bindings.json").toFile(),
                Map.of("scope", "PHASE5_CONTRACTUAL_RESEARCH", "ruleVersion", IncotermRules.VERSION,
                        "frozenImplementationCount", frozenCount, "implementation", bindings,
                        "exampleResponseSha256", sha(out.resolve("example-response.json"))));
    }
    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
