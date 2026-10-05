package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.routing.v2;

import java.util.*;

/** Contextual policies cannot mutate topology. Nonnegative costs and an admissible bound required. */
public interface RouteCostPolicy {
    record Evaluation(boolean allowed, double cost, String reason) {
        public Evaluation {
            if (allowed && (!Double.isFinite(cost) || cost < 0)) throw new IllegalArgumentException("Invalid route cost");
        }
        public static Evaluation blocked(String reason) { return new Evaluation(false, 0, reason); }
    }
    Evaluation evaluate(PhysicalGraph.Edge edge);
    /** Zero is safe for arbitrary contextual policies; override only with a proven lower bound. */
    default double lowerBound(PhysicalGraph.Point from, PhysicalGraph.Point to) { return 0; }

    /** The constraints are explicit caller declarations, not inferred operational facts. */
    record Constraints(Double draftM, Double underKeelClearanceM, Set<Integer> closedEdgeIds,
                       Set<String> prohibitedZones, boolean requireKnownLegalStatus) {
        public Constraints {
            if ((closedEdgeIds != null && closedEdgeIds.stream().anyMatch(Objects::isNull))
                    || (prohibitedZones != null && prohibitedZones.stream().anyMatch(Objects::isNull)))
                throw new IllegalArgumentException("Null closure/zone identifiers are invalid");
            closedEdgeIds = closedEdgeIds == null ? Set.of() : Set.copyOf(closedEdgeIds);
            prohibitedZones = prohibitedZones == null ? Set.of() : Set.copyOf(prohibitedZones);
            for (Double value : Arrays.asList(draftM, underKeelClearanceM))
                if (value != null && (!Double.isFinite(value) || value < 0)) throw new IllegalArgumentException("Invalid draft/clearance");
            if (draftM != null && underKeelClearanceM == null)
                throw new IllegalArgumentException("USER_SUPPLIED underKeelClearanceM is required with draftM (zero must be explicit)");
            if (draftM == null && underKeelClearanceM != null)
                throw new IllegalArgumentException("Clearance requires draftM");
            if (draftM != null && !Double.isFinite(draftM + underKeelClearanceM))
                throw new IllegalArgumentException("Draft/clearance numeric overflow");
        }
    }

    static RouteCostPolicy distance(Constraints constraints) {
        return new RouteCostPolicy() {
            @Override public Evaluation evaluate(PhysicalGraph.Edge edge) {
                if ("PROHIBITED".equals(edge.legalStatus())) return Evaluation.blocked("PROHIBITED_PASSAGE");
                if (constraints.closedEdgeIds().contains(edge.id())) return Evaluation.blocked("USER_SUPPLIED_CLOSURE");
                if (edge.specialZone() != null && constraints.prohibitedZones().contains(edge.specialZone()))
                    return Evaluation.blocked("USER_SUPPLIED_PROHIBITED_ZONE");
                if (!edge.restrictions().isEmpty()) return Evaluation.blocked("UNRESOLVED_HARD_RESTRICTION");
                if (constraints.requireKnownLegalStatus() && !"PERMITTED".equals(edge.legalStatus()))
                    return Evaluation.blocked("LEGAL_STATUS_UNKNOWN");
                if (constraints.draftM() != null) {
                    if (edge.minimumDepthM() == null) return Evaluation.blocked("DEPTH_UNAVAILABLE");
                    if (edge.minimumDepthM() < constraints.draftM() + constraints.underKeelClearanceM())
                        return Evaluation.blocked("INSUFFICIENT_DEPTH");
                }
                return new Evaluation(true, edge.distanceM(), "DISTANCE_METRES");
            }
            @Override public double lowerBound(PhysicalGraph.Point from, PhysicalGraph.Point to) {
                // All edges have verified ellipsoidal polyline distances; metric triangle inequality.
                return from.distanceTo(to) * (1 - 1e-10);
            }
        };
    }
}
