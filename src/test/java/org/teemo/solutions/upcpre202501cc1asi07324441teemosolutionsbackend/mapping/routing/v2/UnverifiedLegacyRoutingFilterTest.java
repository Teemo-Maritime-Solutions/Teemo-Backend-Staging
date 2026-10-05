package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class UnverifiedLegacyRoutingFilterTest {
    @Test void blocksUnsourcedCalculationsButPreservesHistoryAndPorts() throws Exception {
        var filter = new UnverifiedLegacyRoutingFilter(new ObjectMapper());
        for (String path : new String[]{"/api/incoterms/calculate", "/api/routes/calculate-optimal-route",
                "/api/routes/example/recalculate", "/api/ai/predict-weather-delay"}) {
            var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("POST", path), response, chain);
            assertThat(response.getStatus()).isEqualTo(503); assertThat(chain.getRequest()).isNull();
        }
        for (String path : new String[]{"/api/ports", "/api/route-history/example", "/api/v2/maritime/graph"}) {
            var chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isNotNull();
        }
    }
}
