package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation;

import java.math.*;
import java.util.*;
import static org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.sdp.simulation.SimulationModel.*;

/** Finite-support lower quantile and upper-tail integral, including fractional atoms. */
public final class DiscreteRisk {
    static final MathContext MC = MathContext.DECIMAL128;
    public record Atom(BigDecimal value, BigDecimal mass) { }
    private DiscreteRisk() { }
    public static Summary summarize(List<Atom> input, BigDecimal alpha) {
        if (alpha == null || alpha.signum() <= 0 || alpha.compareTo(BigDecimal.ONE) >= 0)
            throw new IllegalArgumentException("ALPHA_MUST_BE_BETWEEN_ZERO_AND_ONE");
        var atoms = sorted(input);
        BigDecimal mass = total(atoms), mean = BigDecimal.ZERO;
        for (var a : atoms) mean = mean.add(a.value().multiply(a.mass()));
        BigDecimal tail = mass.multiply(BigDecimal.ONE.subtract(alpha)), remaining = tail, sum = BigDecimal.ZERO;
        for (int i = atoms.size() - 1; i >= 0 && remaining.signum() > 0; i--) {
            var a = atoms.get(i); var take = a.mass().min(remaining);
            sum = sum.add(a.value().multiply(take)); remaining = remaining.subtract(take);
        }
        return new Summary(mean.divide(mass, MC), quantile(atoms, alpha), sum.divide(tail, MC));
    }
    static List<Atom> sorted(List<Atom> input) {
        if (input == null || input.stream().anyMatch(a -> a == null || a.value() == null || a.mass() == null || a.mass().signum() < 0))
            throw new IllegalArgumentException("INVALID_ATOM");
        var atoms = input.stream().filter(a -> a.mass().signum() > 0)
                .sorted(Comparator.comparing(Atom::value)).toList();
        if (atoms.isEmpty()) throw new IllegalArgumentException("EMPTY_DISTRIBUTION");
        return atoms;
    }
    private static BigDecimal total(List<Atom> atoms) { return atoms.stream().map(Atom::mass).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public static BigDecimal quantile(List<Atom> sorted, BigDecimal level) {
        var threshold = total(sorted).multiply(level); var cumulative = BigDecimal.ZERO;
        for (var a : sorted) {
            cumulative = cumulative.add(a.mass());
            if (cumulative.compareTo(threshold) >= 0) return a.value();
        }
        return sorted.get(sorted.size() - 1).value();
    }
    public static Metric metric(List<Atom> reference, List<Atom> sampled, BigDecimal alpha, double epsilon) {
        var support = sorted(reference); var empirical = sorted(sampled);
        BigDecimal min = support.get(0).value(), max = support.get(support.size() - 1).value();
        var exact = summarize(support, alpha); var estimate = summarize(empirical, alpha);
        BigDecimal eps = BigDecimal.valueOf(epsilon), radius = max.subtract(min).multiply(eps);
        BigDecimal lowLevel = alpha.subtract(eps), highLevel = alpha.add(eps);
        // Known finite support also bounds a tail that happened not to be sampled.
        var qBounds = new Interval(lowLevel.signum() <= 0 ? min : quantile(empirical, lowLevel),
                highLevel.compareTo(BigDecimal.ONE) >= 0 ? max : quantile(empirical, highLevel));
        var cvarRadius = radius.divide(BigDecimal.ONE.subtract(alpha), MC);
        return new Metric(exact, estimate, new SamplingBounds(interval(estimate.mean(), radius, min, max), qBounds,
                interval(estimate.conditionalValueAtRisk(), cvarRadius, min, max)), min, max);
    }
    private static Interval interval(BigDecimal value, BigDecimal radius, BigDecimal min, BigDecimal max) {
        return new Interval(value.subtract(radius).max(min), value.add(radius).min(max));
    }
    static BigDecimal select(Summary summary, Statistic stat) {
        return switch (stat) { case MEAN -> summary.mean(); case VAR -> summary.valueAtRisk(); case CVAR -> summary.conditionalValueAtRisk(); };
    }
}
