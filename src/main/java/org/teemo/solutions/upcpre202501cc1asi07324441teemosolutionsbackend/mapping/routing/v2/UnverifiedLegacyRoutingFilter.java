package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Compatibility tombstone: retain legacy URLs but do not publish unsourced estimates. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UnverifiedLegacyRoutingFilter extends OncePerRequestFilter {
    private static final Set<String> UNSOURCED = Set.of("/api/routes/calculate-optimal-route",
            "/api/routes/distance-between-ports", "/api/incoterms/calculate", "/api/ai/predict-weather-delay");
    private final ObjectMapper mapper;
    public UnverifiedLegacyRoutingFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = org.springframework.web.util.UrlPathHelper.defaultInstance.getLookupPathForRequest(request);
        boolean unsafe = UNSOURCED.contains(path) || path.matches("/api/routes/[^/]+/recalculate/?");
        if (!"OPTIONS".equals(request.getMethod()) && unsafe) {
            response.setStatus(503); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), Map.of("status", "UNVERIFIED_LEGACY_MODEL_DISABLED",
                    "message", "Este calculo utilizaba datos sin procedencia. No se generan estimaciones inventadas.",
                    "researchRoutingEndpoint", "/api/v2/maritime/routes",
                    "documentation", "docs/maritime-routing/api.md"));
            return;
        }
        chain.doFilter(request, response);
    }
}
