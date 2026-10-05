package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather.*;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.GraphCatalog;

@Service
public class PlanWeatherGateway {
    private final WeatherRoutingController weather;
    private final GraphCatalog graphs;
    private final ForecastCatalog forecasts;
    public PlanWeatherGateway(WeatherRoutingController weather, GraphCatalog graphs, ForecastCatalog forecasts) {
        this.weather = weather; this.graphs = graphs; this.forecasts = forecasts;
    }
    public JsonNode route(WeatherRoutingController.Request request) {
        var response = weather.route(request);
        if (!response.getStatusCode().is2xxSuccessful())
            throw new PlanFailure(response.getStatusCode().value(), "PHYSICAL_ROUTE_REJECTED", response.getBody());
        return PlanJson.MAPPER.valueToTree(response.getBody());
    }
    public JsonNode provenance() {
        var graph = graphs.current().orElseThrow(() -> new PlanFailure(503, graphs.status()));
        var forecast = forecasts.current().orElseThrow(() -> new PlanFailure(503, forecasts.status()));
        var node = PlanJson.MAPPER.createObjectNode().put("graphVersion", graph.version()).put("forecastVersion", forecast.version());
        node.set("graphManifest", graph.manifest().deepCopy()); node.set("forecastManifest", forecast.manifest().deepCopy());
        return node;
    }
}
