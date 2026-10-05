package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/incoterms/simulations")
public class SimulationController {
    private final ScenarioSimulation engine;
    private final Semaphore permit = new Semaphore(1);
    private final ObjectMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    public SimulationController(ScenarioSimulation engine) { this.engine = engine; }
    @GetMapping("/model") public Map<String, Object> model() {
        return Map.of("modelVersion", ScenarioSimulation.VERSION, "evidenceStatus", "USER_DECLARED_UNCALIBRATED",
                "maximumAlternatives", 16, "maximumJointScenarios", 256, "maximumSamples", 200000,
                "probabilityDecimalPlaces", 9, "paretoBasis", "EXPLICIT_OBJECTIVES_FINITE_DISTRIBUTION_REFERENCE");
    }
    @PostMapping(consumes = "application/json") public ResponseEntity<?> simulate(@RequestBody String json) {
        if (json.length() > 2097152) return error(413, "REQUEST_TOO_LARGE");
        if (!permit.tryAcquire()) return error(429, "SIMULATION_ALREADY_RUNNING");
        try {
            SimulationModel.Request request;
            try { request = mapper.readValue(json, SimulationModel.Request.class); }
            catch (Exception invalid) { return error(400, "INVALID_JSON_OR_UNSUPPORTED_FIELD"); }
            try { return ResponseEntity.ok(engine.evaluate(request)); }
            catch (IllegalArgumentException invalid) {
                String code = invalid.getMessage();
                int status = code.endsWith("VERSION_MISMATCH") ? 409 : code.startsWith("INCOMPLETE_") ? 422 : 400;
                return error(status, code);
            }
        } finally { permit.release(); }
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidBody() { return error(400, "INVALID_REQUEST_BODY"); }
    private static ResponseEntity<?> error(int status, String code) { return ResponseEntity.status(status).body(Map.of("status", code)); }
}
