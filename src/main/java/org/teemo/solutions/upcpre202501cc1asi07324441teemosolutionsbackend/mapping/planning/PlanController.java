package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/maritime/plans")
public class PlanController {
    private final PlanService service;
    private final Semaphore permit = new Semaphore(1);
    public PlanController(PlanService service) { this.service = service; }
    @GetMapping("/model") public Map<String, Object> model() { return Map.of("planVersion", PlanService.VERSION,
            "maximumPhysicalRoutes", 4, "maximumAlternatives", 16, "aggregateSearchSeconds", 120, "scope", "RESEARCH_NOT_NAVIGATION"); }
    @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable String id) { return guarded(() -> service.get(id)); }
    @GetMapping("/{id}/provenance") public ResponseEntity<?> provenance(@PathVariable String id) { return guarded(() -> {
        var plan = service.get(id); return Map.of("planId", plan.path("planId"), "contentSha256", plan.path("contentSha256"),
                "provenance", plan.path("content").path("provenance"), "bindings", plan.path("content").path("bindings"),
                "inputSha256", plan.path("content").path("inputSha256"), "parent", plan.path("content").path("parent"));
    }); }
    @GetMapping("/{id}/explanation") public ResponseEntity<?> explanation(@PathVariable String id) { return guarded(() -> {
        var plan = service.get(id); return Map.of("planId", plan.path("planId"), "contentSha256", plan.path("contentSha256"),
                "explanation", plan.path("content").path("explanation"), "missingData", plan.path("content").path("missingData"),
                "limitations", plan.path("content").path("limitations"), "changes", plan.path("content").path("changes"));
    }); }
    @PostMapping(consumes="application/json") public ResponseEntity<?> create(@RequestBody String json) {
        return execute(json, () -> service.create(parse(json, PlanModel.Request.class)));
    }
    @PostMapping(value="/{id}/recalculate", consumes="application/json") public ResponseEntity<?> recalculate(@PathVariable String id, @RequestBody String json) {
        return execute(json, () -> service.recalculate(id, parse(json, PlanModel.Recalculation.class)));
    }
    private ResponseEntity<?> execute(String json, Supplier<Object> action) {
        if (json.length() > 2097152) return error(413, "REQUEST_TOO_LARGE", null);
        if (!permit.tryAcquire()) return error(429, "PLAN_BUILD_BUSY", null);
        try { return guarded(action); } finally { permit.release(); }
    }
    private static <T> T parse(String json, Class<T> type) {
        try { return PlanJson.MAPPER.readValue(json, type); }
        catch (Exception invalid) { throw new PlanFailure(400, "INVALID_JSON_OR_UNSUPPORTED_FIELD"); }
    }
    private ResponseEntity<?> guarded(Supplier<Object> action) {
        try { return ResponseEntity.ok().header("Cache-Control", "no-store").body(action.get()); }
        catch (PlanFailure failure) { return error(failure.http, failure.getMessage(), failure.details); }
        catch (IllegalArgumentException invalid) {
            String code = invalid.getMessage() == null ? "INVALID_PLAN_INPUTS" : invalid.getMessage();
            return error(code.endsWith("VERSION_MISMATCH") ? 409 : code.startsWith("INCOMPLETE_") ? 422 : 400, code, null);
        }
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> invalidBody() { return error(400, "INVALID_REQUEST_BODY", null); }
    private static ResponseEntity<?> error(int status, String code, Object details) {
        var body = new LinkedHashMap<String, Object>(); body.put("status", code); body.put("details", details);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }
}
