package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** All coordinates live in explicitly synthetic test resources, never production code. */
final class SyntheticGraphFixture {
    static JsonNode resource() throws IOException {
        try (var input = SyntheticGraphFixture.class.getResourceAsStream("/maritime-routing/synthetic/graph.json")) {
            return new ObjectMapper().readTree(input);
        }
    }
    static PhysicalGraph graph() throws IOException {
        JsonNode fixture = resource();
        List<PhysicalGraph.Node> nodes = new ArrayList<>();
        fixture.path("points").forEach(p -> nodes.add(new PhysicalGraph.Node("SYNTHETIC:" + nodes.size(),
                new PhysicalGraph.Point(p.get(0).asDouble(), p.get(1).asDouble()), "SYNTHETIC", List.of("fixture"))));
        List<PhysicalGraph.Edge> edges = new ArrayList<>();
        fixture.path("directedEdges").forEach(e -> {
            int a = e.get(0).asInt(), b = e.get(1).asInt();
            edges.add(new PhysicalGraph.Edge(edges.size(), a, b, nodes.get(a).point().distanceTo(nodes.get(b).point()),
                    List.of(nodes.get(a).point(), nodes.get(b).point()), null, "SYNTHETIC", null,
                    Set.of(), List.of("fixture"), "UNKNOWN"));
        });
        var manifest = new ObjectMapper().createObjectNode();
        manifest.put("graphVersion", "0".repeat(64));
        manifest.putObject("parameters").put("geodesic_step_m", 2000);
        manifest.putObject("report").putArray("missingData").add("SYNTHETIC_TEST_NOT_PRODUCTION");
        List<PhysicalGraph.Port> ports = List.of(
                new PhysicalGraph.Port("SYNTHETIC:0", "Synthetic origin", nodes.get(0).point(), 0, "CONNECTED_REFERENCE_POINT", List.of("fixture")),
                new PhysicalGraph.Port("SYNTHETIC:3", "Synthetic destination", nodes.get(3).point(), 3, "CONNECTED_REFERENCE_POINT", List.of("fixture")));
        return new PhysicalGraph(nodes, edges, ports, manifest);
    }
}
