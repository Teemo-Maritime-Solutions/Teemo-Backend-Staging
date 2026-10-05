package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import com.fasterxml.jackson.databind.JsonNode;
import net.sf.geographiclib.Geodesic;

import java.util.*;

/** Immutable physical facts and adjacency, with no contextual route weight. */
public final class PhysicalGraph {
    public record Point(double longitude, double latitude) {
        public Point {
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || Math.abs(longitude) > 180 || Math.abs(latitude) > 90)
                throw new IllegalArgumentException("Invalid WGS84 point");
        }
        public double distanceTo(Point other) {
            return Geodesic.WGS84.Inverse(latitude, longitude, other.latitude, other.longitude).s12;
        }
    }
    public record Node(String id, Point point, String kind, List<String> sourceIds) {
        public Node { Objects.requireNonNull(id); Objects.requireNonNull(point); sourceIds = List.copyOf(sourceIds); }
    }
    public record Edge(int id, int from, int to, double distanceM, List<Point> geometry,
                       Double minimumDepthM, String kind, String specialZone, Set<String> restrictions,
                       List<String> sourceIds, String legalStatus, List<String> regionalControlIds) {
        public Edge(int id, int from, int to, double distanceM, List<Point> geometry,
                    Double minimumDepthM, String kind, String specialZone, Set<String> restrictions,
                    List<String> sourceIds, String legalStatus) {
            this(id, from, to, distanceM, geometry, minimumDepthM, kind, specialZone,
                    restrictions, sourceIds, legalStatus, List.of());
        }
        public Edge {
            if (!Double.isFinite(distanceM) || distanceM <= 0 || (minimumDepthM != null
                    && (!Double.isFinite(minimumDepthM) || minimumDepthM < 0)))
                throw new IllegalArgumentException("Invalid physical measurement");
            geometry = List.copyOf(geometry); restrictions = Set.copyOf(restrictions); sourceIds = List.copyOf(sourceIds);
            regionalControlIds = List.copyOf(regionalControlIds);
            if (geometry.size() < 2 || sourceIds.isEmpty()) throw new IllegalArgumentException("Missing edge evidence/geometry");
            if (!Set.of("UNKNOWN", "PERMITTED", "PROHIBITED").contains(legalStatus))
                throw new IllegalArgumentException("Unknown legal status encoding");
        }
    }
    public record Port(String id, String name, Point point, Integer nodeId, String status, List<String> sourceIds) {
        public Port { sourceIds = List.copyOf(sourceIds); }
    }

    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<List<Edge>> adjacency;
    private final Map<String, Port> ports;
    private final JsonNode manifest;
    private final double distanceLowerBoundFactor;

    public PhysicalGraph(List<Node> nodes, List<Edge> edges, List<Port> ports, JsonNode manifest) {
        this.nodes = List.copyOf(nodes); this.edges = List.copyOf(edges); this.manifest = manifest.deepCopy();
        if (nodes.isEmpty()) throw new IllegalArgumentException("Empty graph");
        List<List<Edge>> outgoing = new ArrayList<>(nodes.size());
        Set<String> nodeIds = new HashSet<>();
        for (Node node : nodes) {
            if (!nodeIds.add(node.id())) throw new IllegalArgumentException("Duplicate node id");
            outgoing.add(new ArrayList<>());
        }
        double minimumRatio = 1;
        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            if (edge.id() != i || edge.from() < 0 || edge.from() >= nodes.size() || edge.to() < 0
                    || edge.to() >= nodes.size() || edge.from() == edge.to())
                throw new IllegalArgumentException("Invalid directed edge index");
            if (!edge.geometry().get(0).equals(nodes.get(edge.from()).point())
                    || !edge.geometry().get(edge.geometry().size() - 1).equals(nodes.get(edge.to()).point()))
                throw new IllegalArgumentException("Geometry/node endpoint mismatch");
            double measured = 0;
            for (int p = 1; p < edge.geometry().size(); p++) measured += edge.geometry().get(p - 1).distanceTo(edge.geometry().get(p));
            if (Math.abs(measured - edge.distanceM()) > Math.max(.01, measured * 1e-8))
                throw new IllegalArgumentException("Distance does not match WGS84 geometry");
            if (!Double.isFinite(measured) || measured < 0) throw new IllegalArgumentException("Invalid physical geometry length");
            // H3 centres at different resolutions can coincide. PROJ may report a
            // positive rounding residual where GeographicLib reports zero. Such a
            // transfer already passed the length tolerance above; its nonnegative
            // cost cannot violate the zero geometric lower bound. Never divide by zero.
            if (measured > 0) minimumRatio = Math.min(minimumRatio, edge.distanceM() / measured);
            outgoing.get(edge.from()).add(edge);
        }
        // Preserve source rounding but never let accepted rounding invalidate A*'s lower bound.
        this.distanceLowerBoundFactor = minimumRatio;
        this.adjacency = outgoing.stream().map(List::copyOf).toList();
        Map<String, Port> portIndex = new TreeMap<>();
        for (Port port : ports) {
            if (portIndex.putIfAbsent(port.id(), port) != null) throw new IllegalArgumentException("Duplicate port id");
            if ("CONNECTED_REFERENCE_POINT".equals(port.status()) && port.nodeId() == null)
                throw new IllegalArgumentException("Connected port requires a real graph node");
            if (port.nodeId() != null && (port.nodeId() < 0 || port.nodeId() >= nodes.size()
                    || !nodes.get(port.nodeId()).id().equals(port.id()) || !nodes.get(port.nodeId()).point().equals(port.point())))
                throw new IllegalArgumentException("Invalid port connector reference");
        }
        this.ports = Collections.unmodifiableMap(portIndex);
    }

    public List<Node> nodes() { return nodes; }
    public List<Edge> edges() { return edges; }
    public List<Edge> outgoing(int node) { return adjacency.get(node); }
    public Map<String, Port> ports() { return ports; }
    public JsonNode manifest() { return manifest.deepCopy(); }
    public String version() { return manifest.path("graphVersion").asText(); }
    public double distanceLowerBoundFactor() { return distanceLowerBoundFactor; }
}
