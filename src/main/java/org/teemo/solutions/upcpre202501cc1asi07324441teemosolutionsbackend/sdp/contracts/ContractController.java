package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.contracts;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/incoterms")
public class ContractController {
    private final ContractEvaluator evaluator;
    // Isolated strict mapper avoids changing any frozen routing API configuration.
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public ContractController(ContractEvaluator evaluator) { this.evaluator = evaluator; }
    @GetMapping("/rules")
    public Map<String, Object> catalogue() {
        return Map.of("ruleVersion", IncotermRules.VERSION, "rules", IncotermRules.catalogue(),
                "scope", "ORDINARY_PERFORMANCE_RESEARCH_PARAPHRASE", "edition", "Incoterms 2020");
    }
    @PostMapping(value = "/evaluate", consumes = "application/json")
    public ResponseEntity<?> evaluate(@RequestBody String json) {
        if (json.length() > 262144) return error(413, "REQUEST_TOO_LARGE");
        final ContractModel.Request request;
        try { request = mapper.readValue(json, ContractModel.Request.class); }
        catch (Exception invalid) { return error(400, "INVALID_JSON_OR_UNSUPPORTED_FIELD"); }
        try { return ResponseEntity.ok(evaluator.evaluate(request)); }
        catch (IllegalArgumentException invalid) {
            return error("RULE_VERSION_MISMATCH".equals(invalid.getMessage()) ? 409 : 400, invalid.getMessage());
        }
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidBody() { return error(400, "INVALID_REQUEST_BODY"); }
    private static ResponseEntity<?> error(int status, String code) { return ResponseEntity.status(status).body(Map.of("status", code)); }
}
