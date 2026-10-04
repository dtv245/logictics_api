package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy.Utility;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Pure V1 calculation. Application boundary must supply qualified evidence after hard feasibility. */
@Component
public class OptimizationScoringEngine {
    public record Inputs(boolean feasible, BigDecimal deadheadMiles, BigDecimal candidateLoadedMiles,
                         BigDecimal expectedRevenue, BigDecimal expectedVariableCost,
                         Instant pickupAppointmentStart, Instant predictedArrivalAtPickup,
                         BigDecimal simulatedMinimumHosHeadroomRatio) {}
    public record ComponentResult(BigDecimal rawValue, String rawUnit, BigDecimal normalizedUtility,
                                  BigDecimal weight, BigDecimal contribution) {}
    public record Score(OptimizationScoringPolicy policy, Map<Utility, ComponentResult> components,
                        BigDecimal finalScore) {
        public Score {
            if (policy == null || components == null || components.size() != Utility.values().length || finalScore == null) {
                throw fail("OPTIMIZATION_SCORE_INVALID", "Complete explicit score explanation required");
            }
            BigDecimal sum = BigDecimal.ZERO;
            for (Utility utility : Utility.values()) {
                ComponentResult component = components.get(utility);
                if (component == null || component.rawValue() == null || component.rawUnit() == null
                        || component.normalizedUtility() == null || component.weight() == null || component.contribution() == null
                        || component.normalizedUtility().signum() < 0 || component.normalizedUtility().compareTo(BigDecimal.ONE) > 0
                        || component.normalizedUtility().scale() != policy.numeric().utilityScale()
                        || component.weight().scale() != policy.numeric().weightScale()
                        || component.weight().compareTo(policy.weights().get(utility)) != 0
                        || component.contribution().scale() != policy.numeric().contributionScale()
                        || component.contribution().compareTo(policy.numeric().contribution(
                                component.normalizedUtility().multiply(component.weight(), policy.numeric().context()))) != 0) {
                    throw fail("OPTIMIZATION_SCORE_INVALID", "Stored component precision/weight/contribution is inconsistent");
                }
                sum = sum.add(component.contribution());
            }
            if (finalScore.scale() != policy.numeric().scoreScale() || finalScore.compareTo(sum) != 0) {
                throw fail("OPTIMIZATION_SCORE_INVALID", "Final score must equal stored contributions exactly");
            }
            components = Map.copyOf(components);
        }
    }
    public record CandidateScore(UUID candidateId, Score score) {}
    public record RankedCandidate(UUID candidateId, Score score, int rank) {}

    public Score score(Inputs input, OptimizationScoringPolicy policy) {
        if (policy == null) throw fail("INVALID_OPTIMIZATION_POLICY", "Explicit published policy is required");
        if (input == null || !input.feasible()) throw fail("OPTIMIZATION_INFEASIBLE", "Hard feasibility must pass before scoring");
        if (input.candidateLoadedMiles() == null || input.candidateLoadedMiles().signum() <= 0
                || input.deadheadMiles() == null || input.deadheadMiles().signum() < 0) {
            throw fail("DEADHEAD_UTILITY_UNAVAILABLE", "Positive loaded route miles and qualified nonnegative deadhead required");
        }
        if (input.expectedRevenue() == null || input.expectedRevenue().signum() <= 0) {
            throw fail("MARGIN_UTILITY_UNAVAILABLE", "Positive accepted revenue required");
        }
        if (input.expectedVariableCost() == null || input.expectedVariableCost().signum() < 0) {
            throw fail("FORECAST_COST_INCOMPLETE", "Qualified complete variable cost required");
        }
        if (input.pickupAppointmentStart() == null || input.predictedArrivalAtPickup() == null) {
            throw fail("ON_TIME_UTILITY_UNAVAILABLE", "Authoritative appointment and predicted arrival required");
        }
        if (input.simulatedMinimumHosHeadroomRatio() == null) {
            throw fail("HOS_UTILITY_EVIDENCE_REQUIRED", "Full simulated HOS headroom required");
        }
        var context = policy.numeric().context();
        BigDecimal deadhead = input.deadheadMiles().divide(input.candidateLoadedMiles(), context);
        BigDecimal margin = input.expectedRevenue().subtract(input.expectedVariableCost(), context)
                .divide(input.expectedRevenue(), context);
        Duration slack = Duration.between(input.predictedArrivalAtPickup(), input.pickupAppointmentStart());
        BigDecimal minutes = BigDecimal.valueOf(slack.getSeconds()).add(BigDecimal.valueOf(slack.getNano(), 9))
                .divide(BigDecimal.valueOf(60), context);
        var raw = Map.of(Utility.DEADHEAD, deadhead, Utility.MARGIN, margin, Utility.ON_TIME, minutes,
                Utility.HOS, input.simulatedMinimumHosHeadroomRatio());
        var components = new EnumMap<Utility, ComponentResult>(Utility.class);
        BigDecimal sum = BigDecimal.ZERO;
        for (Utility utility : Utility.values()) {
            BigDecimal normalized = normalize(utility, raw.get(utility), policy);
            BigDecimal weight = policy.weights().get(utility);
            BigDecimal contribution = policy.numeric().contribution(normalized.multiply(weight, context));
            components.put(utility, new ComponentResult(raw.get(utility), utility == Utility.ON_TIME ? "MINUTE" : "RATIO",
                    normalized, weight, contribution));
            sum = sum.add(contribution);
        }
        return new Score(policy, components, policy.numeric().score(sum));
    }

    public BigDecimal normalize(Utility utility, BigDecimal raw, OptimizationScoringPolicy policy) {
        if (utility == null || raw == null || policy == null) throw fail("INVALID_OPTIMIZATION_POLICY", "Explicit raw utility and policy required");
        var knots = policy.curves().get(utility);
        BigDecimal value = knots.getFirst().utility();
        if (raw.compareTo(knots.getLast().raw()) >= 0) value = knots.getLast().utility();
        else if (raw.compareTo(knots.getFirst().raw()) > 0) {
            for (int i = 1; i < knots.size(); i++) {
                var low = knots.get(i - 1);
                var high = knots.get(i);
                if (raw.compareTo(high.raw()) <= 0) {
                    var context = policy.numeric().context();
                    BigDecimal fraction = raw.subtract(low.raw(), context).divide(high.raw().subtract(low.raw(), context), context);
                    value = low.utility().add(high.utility().subtract(low.utility(), context).multiply(fraction, context), context);
                    break;
                }
            }
        }
        return policy.numeric().utility(value);
    }

    public List<RankedCandidate> denseRanks(List<CandidateScore> candidates) {
        if (candidates == null) throw fail("INVALID_OPTIMIZATION_CANDIDATE", "Candidate list required");
        var identities = new HashSet<UUID>();
        for (CandidateScore candidate : candidates) {
            if (candidate == null || candidate.candidateId() == null || candidate.score() == null
                    || !identities.add(candidate.candidateId())) {
                throw fail("INVALID_OPTIMIZATION_CANDIDATE", "Distinct scored candidate identities required");
            }
        }
        var sorted = candidates.stream().sorted(Comparator
                .comparing((CandidateScore c) -> c.score().finalScore()).reversed()
                .thenComparing(CandidateScore::candidateId)).toList(); // UUID presentation only, never a business winner.
        var ranks = new ArrayList<RankedCandidate>();
        BigDecimal previous = null;
        int rank = 0;
        for (CandidateScore candidate : sorted) {
            if (previous == null || candidate.score().finalScore().compareTo(previous) != 0) rank++;
            ranks.add(new RankedCandidate(candidate.candidateId(), candidate.score(), rank));
            previous = candidate.score().finalScore();
        }
        return List.copyOf(ranks);
    }
    private static BadRequestException fail(String code, String message) { return new BadRequestException(code, message); }
}
