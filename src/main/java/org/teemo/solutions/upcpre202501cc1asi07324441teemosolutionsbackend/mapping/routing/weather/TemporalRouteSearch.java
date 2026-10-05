package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.weather;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import net.sf.geographiclib.Geodesic;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2.*;

/** A* over (node,time slot), not a FIFO/single-label search. Waiting is an explicit scenario assumption. */
public final class TemporalRouteSearch {
    public record Options(int timeStepSeconds, int horizonHours, int maximumExpandedStates,
                          int maximumLabels, int maximumRuntimeSeconds, boolean acknowledgeModelledWaiting) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name, Object value) { throw new IllegalArgumentException("Unsupported search parameter"); }
        public Options {
            if (timeStepSeconds < 60 || timeStepSeconds > 3600 || 3600%timeStepSeconds != 0
                    || horizonHours < 1 || horizonHours > 168 || maximumExpandedStates < 1 || maximumExpandedStates > 200000
                    || maximumLabels < 1 || maximumLabels > 500000 || maximumRuntimeSeconds < 1 || maximumRuntimeSeconds > 120
                    || !acknowledgeModelledWaiting)
                throw new IllegalArgumentException("Bounded search and explicit modelled-waiting acknowledgment required");
        }
    }
    public record Leg(Integer edgeId, String fromNodeId, String toNodeId, @JsonFormat(shape=JsonFormat.Shape.STRING) Instant departure,
                      @JsonFormat(shape=JsonFormat.Shape.STRING) Instant arrivalBeforeRounding,
                      @JsonFormat(shape=JsonFormat.Shape.STRING) Instant nextStateTime, double sailingSeconds,
                      double modelledWaitingSeconds, double distanceM, double maximumSampledWaveM) { }
    public record Result(String status, List<Leg> legs, @JsonFormat(shape=JsonFormat.Shape.STRING) Instant arrival, double sailingSeconds,
                         double modelledWaitingSeconds, double distanceM, int expandedStates,
                         int labels, Map<String, Integer> rejectedEvaluations, String optimality) { }
    private record State(int node, int slot) { }
    private record Label(State state, double priority, Label previous, Leg leg) { }
    private record Traversal(double seconds, double maximumWaveM) { }
    private final PhysicalGraph graph;
    private final WeatherField forecast;
    private final VesselModel vessel;
    private final RouteCostPolicy physical;
    private final Options options;
    private final Map<String, Integer> rejected = new TreeMap<>();
    private long deadline;
    private static final class Budget extends RuntimeException { }

    public TemporalRouteSearch(PhysicalGraph graph, WeatherField forecast, VesselModel vessel,
                               RouteCostPolicy.Constraints constraints, Options options) {
        this.graph = graph; this.forecast = forecast; this.vessel = vessel;
        this.physical = RouteCostPolicy.distance(constraints); this.options = options;
    }
    private void checkBudget() {
        if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new Budget();
    }
    private static Instant plus(Instant time, double seconds) { return time.plusNanos((long)Math.ceil(seconds*1e9)); }
    private void hold(PhysicalGraph.Point point, Instant from, Instant until) {
        for (Instant time = from; time.isBefore(until); time = time.plusSeconds(300)) {
            checkBudget(); vessel.checkLimits(forecast.sample(point, time));
        }
        vessel.checkLimits(forecast.sample(point, until));
    }
    private Traversal traverse(PhysicalGraph.Edge edge, Instant departure, Instant end) {
        double elapsed = 0, maximumWave = 0;
        for (int segment = 1; segment < edge.geometry().size(); segment++) {
            var a = edge.geometry().get(segment-1); var b = edge.geometry().get(segment);
            var inverse = Geodesic.WGS84.Inverse(a.latitude(), a.longitude(), b.latitude(), b.longitude());
            double distance = 0;
            while (distance < inverse.s12-1e-6) {
                checkBudget();
                var position = Geodesic.WGS84.Direct(a.latitude(), a.longitude(), inverse.azi1, distance);
                var weather = forecast.sample(new PhysicalGraph.Point(position.lon2, position.lat2), plus(departure, elapsed));
                maximumWave = Math.max(maximumWave, weather.waveHeightM());
                double speed = vessel.groundSpeedMps(weather, position.azi2);
                double stepM = Math.min(inverse.s12-distance, Math.min(2500, speed*300));
                elapsed += stepM/speed; distance += stepM;
                if (plus(departure, elapsed).isAfter(end)) throw new WeatherField.Unavailable("FORECAST_OR_REQUEST_HORIZON");
            }
            var endpointWeather = forecast.sample(b, plus(departure, elapsed));
            vessel.groundSpeedMps(endpointWeather, inverse.azi2);
            maximumWave = Math.max(maximumWave, endpointWeather.waveHeightM());
        }
        return new Traversal(elapsed, maximumWave);
    }
    public Result search(int origin, int destination, Instant departure) {
        if (origin < 0 || destination < 0 || origin >= graph.nodes().size() || destination >= graph.nodes().size()
                || origin == destination) throw new IllegalArgumentException("Distinct existing graph nodes required");
        if (departure.isBefore(forecast.start()) || !departure.isBefore(forecast.end()))
            throw new IllegalArgumentException("Departure outside forecast horizon");
        rejected.clear(); deadline = System.nanoTime()+options.maximumRuntimeSeconds()*1_000_000_000L;
        Instant requestedEnd = departure.plusSeconds(options.horizonHours()*3600L);
        Instant end = requestedEnd.isBefore(forecast.end()) ? requestedEnd : forecast.end();
        int maximumSlot = (int)(Duration.between(departure, end).getSeconds()/options.timeStepSeconds());
        double maximumSpeed = vessel.calmSpeedKnots()*VesselModel.KNOT_MPS+forecast.maximumCurrentMps();
        if (!Double.isFinite(maximumSpeed) || maximumSpeed <= 0) throw new IllegalArgumentException("Invalid forecast speed bound");
        double[] heuristic = new double[graph.nodes().size()]; Arrays.fill(heuristic, -1);
        java.util.function.IntToDoubleFunction bound = node -> {
            if (heuristic[node] < 0) heuristic[node] = graph.nodes().get(node).point().distanceTo(graph.nodes().get(destination).point())
                    /maximumSpeed*(1-1e-9)*graph.distanceLowerBoundFactor();
            return heuristic[node];
        };
        var queue = new PriorityQueue<Label>(Comparator.comparingDouble(Label::priority)
                .thenComparingInt(l -> l.state().slot()).thenComparingInt(l -> l.state().node()));
        Set<State> seen = new HashSet<>(); State initial = new State(origin, 0); seen.add(initial);
        queue.add(new Label(initial, bound.applyAsDouble(origin), null, null));
        int expanded = 0;
        try {
            while (!queue.isEmpty()) {
                checkBudget(); Label current = queue.poll(); int node = current.state().node(), slot = current.state().slot();
                if (node == destination) return found(current, departure, expanded, seen.size());
                if (expanded >= options.maximumExpandedStates()) throw new Budget();
                expanded++;
                if (slot >= maximumSlot) continue;
                Instant at = departure.plusSeconds((long)slot*options.timeStepSeconds());
                for (var edge : graph.outgoing(node)) {
                    checkBudget();
                    var eligibility = physical.evaluate(edge);
                    if (!eligibility.allowed()) { reject(eligibility.reason()); continue; }
                    if ("PANAMA_CANAL_RESEARCH".equals(edge.specialZone())) {
                        reject("CANAL_LOCK_TRANSIT_TIME_MODEL_UNAVAILABLE"); continue;
                    }
                    if ("SUEZ_CANAL_RESEARCH".equals(edge.specialZone())) {
                        reject("SUEZ_TRANSIT_TIME_MODEL_UNAVAILABLE"); continue;
                    }
                    try {
                        Traversal travel = traverse(edge, at, end);
                        int nextSlot = slot+Math.max(1, (int)Math.ceil(travel.seconds()/options.timeStepSeconds()));
                        if (nextSlot > maximumSlot) { reject("FORECAST_OR_REQUEST_HORIZON"); continue; }
                        State next = new State(edge.to(), nextSlot);
                        if (seen.contains(next)) continue;
                        Instant arrival = plus(at, travel.seconds());
                        Instant rounded = departure.plusSeconds((long)nextSlot*options.timeStepSeconds());
                        hold(graph.nodes().get(edge.to()).point(), arrival, rounded);
                        var leg = new Leg(edge.id(), graph.nodes().get(node).id(), graph.nodes().get(edge.to()).id(),
                                at, arrival, rounded, travel.seconds(), (nextSlot-slot)*(double)options.timeStepSeconds()-travel.seconds(),
                                edge.distanceM(), travel.maximumWaveM());
                        add(queue, seen, new Label(next, nextSlot*(double)options.timeStepSeconds()+bound.applyAsDouble(edge.to()), current, leg));
                    } catch (WeatherField.Unavailable unavailable) { reject(unavailable.getMessage()); }
                }
                State waiting = new State(node, slot+1);
                if (!seen.contains(waiting)) try {
                    Instant until = at.plusSeconds(options.timeStepSeconds());
                    hold(graph.nodes().get(node).point(), at, until);
                    var leg = new Leg(null, graph.nodes().get(node).id(), graph.nodes().get(node).id(), at, at, until,
                            0, options.timeStepSeconds(), 0, 0);
                    add(queue, seen, new Label(waiting, (slot+1)*(double)options.timeStepSeconds()+bound.applyAsDouble(node), current, leg));
                } catch (WeatherField.Unavailable unavailable) { reject(unavailable.getMessage()); }
            }
            return empty("NO_ROUTE_WITHIN_MODEL_HORIZON", expanded, seen.size());
        } catch (Budget exhausted) { return empty("SEARCH_BUDGET_EXHAUSTED", expanded, seen.size()); }
    }
    private void add(PriorityQueue<Label> queue, Set<State> seen, Label label) {
        if (seen.size() >= options.maximumLabels()) throw new Budget();
        seen.add(label.state()); queue.add(label);
    }
    private void reject(String reason) { rejected.merge(reason, 1, Integer::sum); }
    private Result empty(String status, int expanded, int labels) {
        return new Result(status, List.of(), null, 0, 0, 0, expanded, labels, Map.copyOf(rejected), "NOT_ESTABLISHED");
    }
    private Result found(Label label, Instant departure, int expanded, int labels) {
        LinkedList<Leg> legs = new LinkedList<>();
        for (Label cursor = label; cursor.previous() != null; cursor = cursor.previous()) legs.addFirst(cursor.leg());
        return new Result("FOUND_RESEARCH_SCENARIO", List.copyOf(legs),
                departure.plusSeconds((long)label.state().slot()*options.timeStepSeconds()),
                legs.stream().mapToDouble(Leg::sailingSeconds).sum(), legs.stream().mapToDouble(Leg::modelledWaitingSeconds).sum(),
                legs.stream().mapToDouble(Leg::distanceM).sum(), expanded, labels, Map.copyOf(rejected),
                "EARLIEST_ARRIVAL_IN_DISCRETE_MODEL_WITH_NUMERICAL_WEATHER_INTEGRATION");
    }
}
