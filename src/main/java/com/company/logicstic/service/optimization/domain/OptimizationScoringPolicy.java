package com.company.logicstic.service.optimization.domain;

import com.company.logicstic.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Explicit published V1 contract from OPT-DEC-006/007/008, never a missing-policy fallback. */
public record OptimizationScoringPolicy(
        PolicyReference utilityPolicy, PolicyReference weightPolicy, PolicyReference numericPolicy,
        Map<Utility, List<Knot>> curves, Map<Utility, BigDecimal> weights, NumericPolicy numeric) {

    public enum Utility { DEADHEAD, MARGIN, ON_TIME, HOS }
    public record PolicyReference(String code, int version) {}
    public record Knot(BigDecimal raw, BigDecimal utility) {}
    public record NumericPolicy(int intermediatePrecision, RoundingMode roundingMode,
                                int utilityScale, int weightScale, int contributionScale, int scoreScale) {
        public MathContext context() { return new MathContext(intermediatePrecision, roundingMode); }
        public BigDecimal utility(BigDecimal raw) { return raw.setScale(utilityScale, roundingMode); }
        public BigDecimal contribution(BigDecimal raw) { return raw.setScale(contributionScale, roundingMode); }
        public BigDecimal score(BigDecimal raw) { return raw.setScale(scoreScale, roundingMode); }
    }

    public OptimizationScoringPolicy {
        if (utilityPolicy == null || weightPolicy == null || numericPolicy == null || numeric == null
                || curves == null || curves.size() != Utility.values().length) {
            throw invalid("Complete explicit scoring policy is required");
        }
        if (weights == null || weights.size() != Utility.values().length) throw invalidWeights();
        var curveCopy = new EnumMap<Utility, List<Knot>>(Utility.class);
        var weightCopy = new EnumMap<Utility, BigDecimal>(Utility.class);
        BigDecimal sum = BigDecimal.ZERO;
        for (Utility utility : Utility.values()) {
            List<Knot> knots = curves.get(utility);
            if (knots == null || knots.size() < 2) throw invalid("Each utility needs an explicit curve");
            BigDecimal previous = null;
            for (Knot knot : knots) {
                if (knot == null || knot.raw() == null || knot.utility() == null
                        || knot.utility().signum() < 0 || knot.utility().compareTo(BigDecimal.ONE) > 0
                        || previous != null && knot.raw().compareTo(previous) <= 0) {
                    throw invalid("Curve knots must be strictly ordered with utility in [0,1]");
                }
                previous = knot.raw();
            }
            curveCopy.put(utility, List.copyOf(knots));
            BigDecimal weight = weights.get(utility);
            if (weight == null || weight.signum() < 0) throw invalidWeights();
            try { weight = weight.setScale(6, RoundingMode.UNNECESSARY); }
            catch (ArithmeticException ex) { throw invalidWeights(); }
            weightCopy.put(utility, weight);
            sum = sum.add(weight);
        }
        if (sum.compareTo(BigDecimal.ONE) != 0) throw invalidWeights();
        curves = Map.copyOf(curveCopy);
        weights = Map.copyOf(weightCopy);
        if (!new PolicyReference("OPT_UTILITY_V1", 1).equals(utilityPolicy)
                || !new PolicyReference("OPT_WEIGHT_V1", 1).equals(weightPolicy)
                || !new PolicyReference("OPT_NUMERIC_V1", 1).equals(numericPolicy)
                || !approvedNumeric().equals(numeric) || !matchesApprovedCurves(curves)
                || !sameWeights(weights, approvedWeights())) {
            throw invalid("Unapproved scoring policy/version; V1 must preserve its confirmed contract");
        }
    }

    public static OptimizationScoringPolicy confirmedV1() {
        return new OptimizationScoringPolicy(new PolicyReference("OPT_UTILITY_V1", 1),
                new PolicyReference("OPT_WEIGHT_V1", 1), new PolicyReference("OPT_NUMERIC_V1", 1),
                approvedCurves(), approvedWeights(), approvedNumeric());
    }

    private static NumericPolicy approvedNumeric() {
        return new NumericPolicy(MathContext.DECIMAL128.getPrecision(), RoundingMode.HALF_EVEN, 6, 6, 8, 8);
    }
    private static Map<Utility, BigDecimal> approvedWeights() {
        return Map.of(Utility.DEADHEAD, new BigDecimal("0.250000"), Utility.MARGIN, new BigDecimal("0.350000"),
                Utility.ON_TIME, new BigDecimal("0.300000"), Utility.HOS, new BigDecimal("0.100000"));
    }
    private static Map<Utility, List<Knot>> approvedCurves() {
        return Map.of(Utility.DEADHEAD, knots("0", "1", ".10", ".90", ".25", ".70", ".50", ".40", "1", "0"),
                Utility.MARGIN, knots("0", "0", ".05", ".25", ".10", ".50", ".20", ".80", ".30", "1"),
                Utility.ON_TIME, knots("0", "0", "30", ".25", "60", ".50", "120", ".80", "180", "1"),
                Utility.HOS, knots("0", "0", ".05", ".25", ".10", ".50", ".20", ".80", ".30", "1"));
    }
    private static List<Knot> knots(String... pairs) {
        var result = new java.util.ArrayList<Knot>();
        for (int i = 0; i < pairs.length; i += 2) result.add(new Knot(new BigDecimal(pairs[i]), new BigDecimal(pairs[i + 1])));
        return List.copyOf(result);
    }
    private static boolean matchesApprovedCurves(Map<Utility, List<Knot>> actual) {
        var approved = approvedCurves();
        for (Utility utility : Utility.values()) {
            if (actual.get(utility).size() != approved.get(utility).size()) return false;
            for (int i = 0; i < actual.get(utility).size(); i++) {
                Knot a = actual.get(utility).get(i), b = approved.get(utility).get(i);
                if (a.raw().compareTo(b.raw()) != 0 || a.utility().compareTo(b.utility()) != 0) return false;
            }
        }
        return true;
    }
    private static boolean sameWeights(Map<Utility, BigDecimal> actual, Map<Utility, BigDecimal> expected) {
        return java.util.Arrays.stream(Utility.values()).allMatch(u -> actual.get(u).compareTo(expected.get(u)) == 0);
    }
    private static BadRequestException invalid(String message) {
        return new BadRequestException("INVALID_OPTIMIZATION_POLICY", message);
    }
    private static BadRequestException invalidWeights() {
        return new BadRequestException("OPTIMIZATION_WEIGHT_POLICY_INVALID", "All four nonnegative scale-6 weights must sum exactly to one");
    }
}
