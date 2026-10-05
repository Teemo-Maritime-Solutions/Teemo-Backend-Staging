package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import java.util.*;

/** Directed A* and bounded Yen loopless candidates. No fallback when topology is disconnected. */
public final class RouteSearch {
    public record Path(List<Integer> nodes, List<PhysicalGraph.Edge> edges, double cost, double distanceM) {
        public Path { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
        public List<Integer> signature() { return edges.stream().map(PhysicalGraph.Edge::id).toList(); }
    }
    public record Result(List<Path> paths, int candidates, long expandedNodes, boolean budgetExhausted,
                         Map<String, Long> rejectedEvaluations, int rejectedForSimilarity) {
        public Result { paths = List.copyOf(paths); rejectedEvaluations = Map.copyOf(rejectedEvaluations); }
    }
    private record Label(int node, double g, double f) {}
    private final PhysicalGraph graph;
    private final RouteCostPolicy policy;
    private final long maxExpansions;
    private long expanded;
    private final Map<String, Long> rejected = new TreeMap<>();
    private final Map<Integer, RouteCostPolicy.Evaluation> evaluations = new HashMap<>();
    private boolean exhausted;

    public RouteSearch(PhysicalGraph graph, RouteCostPolicy policy, long maxExpansions) {
        if (maxExpansions < 1 || maxExpansions > 20_000_000) throw new IllegalArgumentException("Invalid search budget");
        this.graph = graph; this.policy = policy; this.maxExpansions = maxExpansions;
    }

    public Optional<Path> shortest(int start, int end, Set<Integer> blockedEdges, Set<Integer> blockedNodes) {
        if (start < 0 || end < 0 || start >= graph.nodes().size() || end >= graph.nodes().size())
            throw new IllegalArgumentException("Unknown node");
        if (blockedNodes.contains(start) || blockedNodes.contains(end)) return Optional.empty();
        double[] scores = new double[graph.nodes().size()]; Arrays.fill(scores, Double.POSITIVE_INFINITY);
        int[] predecessors = new int[scores.length]; Arrays.fill(predecessors, -1);
        PriorityQueue<Label> queue = new PriorityQueue<>(Comparator.comparingDouble(Label::f).thenComparingInt(Label::node));
        scores[start] = 0; queue.add(new Label(start, 0, heuristic(start, end)));
        while (!queue.isEmpty()) {
            Label current = queue.poll();
            if (current.g() > scores[current.node()]) continue;
            if (current.node() == end) {
                List<PhysicalGraph.Edge> edges = new ArrayList<>();
                for (int n = end; n != start;) {
                    PhysicalGraph.Edge edge = graph.edges().get(predecessors[n]); edges.add(edge); n = edge.from();
                }
                Collections.reverse(edges);
                return Optional.of(path(start, edges));
            }
            if (expanded >= maxExpansions) { exhausted = true; return Optional.empty(); }
            expanded++;
            for (PhysicalGraph.Edge edge : graph.outgoing(current.node())) {
                if (blockedEdges.contains(edge.id()) || blockedNodes.contains(edge.to())) continue;
                RouteCostPolicy.Evaluation evaluation = evaluate(edge);
                if (!evaluation.allowed()) { rejected.merge(evaluation.reason(), 1L, Long::sum); continue; }
                double score = current.g() + evaluation.cost();
                if (!Double.isFinite(score)) throw new IllegalArgumentException("Route cost overflow");
                if (score < scores[edge.to()]) {
                    scores[edge.to()] = score; predecessors[edge.to()] = edge.id();
                    queue.add(new Label(edge.to(), score, score + heuristic(edge.to(), end)));
                }
            }
        }
        return Optional.empty();
    }

    private double heuristic(int from, int to) {
        double result = policy.lowerBound(graph.nodes().get(from).point(), graph.nodes().get(to).point())
                * graph.distanceLowerBoundFactor();
        if (!Double.isFinite(result) || result < 0) throw new IllegalArgumentException("Invalid policy lower bound");
        return result;
    }
    private RouteCostPolicy.Evaluation evaluate(PhysicalGraph.Edge edge) {
        return evaluations.computeIfAbsent(edge.id(), ignored -> policy.evaluate(edge));
    }
    private Path path(int start, List<PhysicalGraph.Edge> edges) {
        List<Integer> nodes = new ArrayList<>(); nodes.add(start);
        double cost = 0, distance = 0;
        for (PhysicalGraph.Edge edge : edges) { nodes.add(edge.to()); cost += evaluate(edge).cost(); distance += edge.distanceM(); }
        return new Path(nodes, edges, cost, distance);
    }

    public Result alternatives(int start, int end, int k, int maxCandidates, double minimumSeparationM) {
        if (k < 1 || k > 8 || maxCandidates < k || maxCandidates > 100 || !Double.isFinite(minimumSeparationM) || minimumSeparationM < 0)
            throw new IllegalArgumentException("Invalid alternative search parameters");
        Optional<Path> first = shortest(start, end, Set.of(), Set.of());
        if (first.isEmpty()) return new Result(List.of(), 0, expanded, exhausted, rejected, 0);
        List<Path> enumerated = new ArrayList<>(List.of(first.get())), accepted = new ArrayList<>(enumerated);
        // Dense multi-resolution meshes can have thousands of almost-identical Yen paths.
        // Obtain geographically separated candidates under temporary exclusion constraints,
        // without claiming that the exclusion is an observed hazard or changing any cost.
        int geographicAttempts = 0;
        if (minimumSeparationM > 0 && k > 1) {
            for (int reference = 0; reference < accepted.size() && accepted.size() < k && !exhausted; reference++) {
                Path basis = accepted.get(reference);
                for (double fraction : new double[]{.5, .25, .75}) {
                    if (accepted.size() >= k || exhausted || geographicAttempts + enumerated.size() >= maxCandidates) break;
                    int centre = interiorNode(basis, fraction);
                    if (centre == start || centre == end) continue;
                    geographicAttempts++;
                    Set<Integer> exclusion = geographicExclusion(centre, minimumSeparationM);
                    Optional<Path> alternative = shortest(start, end, exclusion, Set.of());
                    if (alternative.isPresent() && accepted.stream().allMatch(p -> geographicallySeparated(p, alternative.get(), minimumSeparationM)))
                        accepted.add(alternative.get());
                }
                if (geographicAttempts + enumerated.size() >= maxCandidates) break;
            }
        }
        Set<List<Integer>> seen = new HashSet<>(); seen.add(first.get().signature());
        PriorityQueue<Path> candidates = new PriorityQueue<>(Comparator.comparingDouble(Path::cost)
                .thenComparing(p -> p.signature().toString()));
        int similar = 0;
        while (accepted.size() < k && enumerated.size() + geographicAttempts < maxCandidates && !exhausted) {
            Path previous = enumerated.get(enumerated.size() - 1);
            for (int spur = 0; spur < previous.edges().size() && !exhausted; spur++) {
                List<PhysicalGraph.Edge> root = previous.edges().subList(0, spur);
                Set<Integer> blockedEdges = new HashSet<>();
                for (Path existing : enumerated)
                    if (existing.edges().size() > spur && existing.edges().subList(0, spur).equals(root))
                        blockedEdges.add(existing.edges().get(spur).id());
                Set<Integer> blockedNodes = new HashSet<>(previous.nodes().subList(0, spur));
                Optional<Path> tail = shortest(previous.nodes().get(spur), end, blockedEdges, blockedNodes);
                if (tail.isPresent()) {
                    List<PhysicalGraph.Edge> combined = new ArrayList<>(root); combined.addAll(tail.get().edges());
                    Path candidate = path(start, combined);
                    if (seen.add(candidate.signature())) candidates.add(candidate);
                }
            }
            // Incomplete spur enumeration cannot promise the globally next-shortest candidate.
            if (exhausted || candidates.isEmpty()) break;
            Path candidate = candidates.poll(); enumerated.add(candidate);
            if (accepted.stream().anyMatch(p -> p.signature().equals(candidate.signature()))) continue;
            if (accepted.stream().allMatch(p -> geographicallySeparated(p, candidate, minimumSeparationM))) accepted.add(candidate);
            else similar++;
        }
        accepted.sort(Comparator.comparingDouble(Path::cost).thenComparing(p -> p.signature().toString()));
        return new Result(accepted, enumerated.size() + geographicAttempts, expanded, exhausted, rejected, similar);
    }

    private int interiorNode(Path path, double fraction) {
        double covered = 0, target = path.distanceM() * fraction;
        for (PhysicalGraph.Edge edge : path.edges()) {
            covered += edge.distanceM();
            if (covered >= target) return edge.to();
        }
        return path.nodes().get(path.nodes().size() - 1);
    }
    private Set<Integer> geographicExclusion(int centre, double radiusM) {
        PhysicalGraph.Point origin = graph.nodes().get(centre).point();
        double[] distances = new double[graph.nodes().size()];
        for (int i = 0; i < distances.length; i++) distances[i] = origin.distanceTo(graph.nodes().get(i).point());
        Set<Integer> blocked = new HashSet<>();
        for (PhysicalGraph.Edge edge : graph.edges()) {
            // Metric lower bound to every point of an edge's complete polyline. Removing
            // uncertain intersections is conservative and also handles long coarse edges.
            if (Math.max(distances[edge.from()], distances[edge.to()]) - edge.distanceM() < radiusM)
                blocked.add(edge.id());
        }
        return blocked;
    }

    private boolean geographicallySeparated(Path a, Path b, double minimumM) {
        if (a.signature().equals(b.signature())) return false;
        if (minimumM == 0) return true; // Caller explicitly disables diversity; response must say so.
        // Sufficient (conservative) separation test: d(point, endpoint) - segment length
        // is a metric lower bound on distance to any point of that geodesic segment.
        // Unlike node/edge overlap, this cannot mistake different-resolution nodes for separation.
        return separatedFrom(a, b, minimumM) || separatedFrom(b, a, minimumM);
    }
    private boolean separatedFrom(Path a, Path b, double minimumM) {
        for (int node : a.nodes()) {
            PhysicalGraph.Point point = graph.nodes().get(node).point();
            double lower = Double.POSITIVE_INFINITY;
            for (PhysicalGraph.Edge edge : b.edges()) {
                for (int i = 1; i < edge.geometry().size(); i++) {
                    PhysicalGraph.Point x = edge.geometry().get(i - 1), y = edge.geometry().get(i);
                    double bound = Math.max(point.distanceTo(x), point.distanceTo(y)) - x.distanceTo(y);
                    lower = Math.min(lower, Math.max(0, bound));
                }
            }
            if (lower >= minimumM) return true;
        }
        return false;
    }
}
