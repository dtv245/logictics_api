package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.optimization.OptimizationScoringEngine.CandidateScore;
import com.company.logicstic.service.optimization.OptimizationScoringEngine.Inputs;
import com.company.logicstic.service.optimization.OptimizationScoringEngine.Score;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy;
import com.company.logicstic.service.optimization.domain.OptimizationScoringPolicy.Utility;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationScoringEngineTest {
    private final OptimizationScoringEngine engine = new OptimizationScoringEngine();
    private final OptimizationScoringPolicy policy = OptimizationScoringPolicy.confirmedV1();
    private final Instant appointment = Instant.parse("2026-10-05T10:00:00Z");
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private Inputs input(String deadhead, String miles, String revenue, String cost, long slackSeconds, String hos) {
        return new Inputs(true, deadhead == null ? null : d(deadhead), miles == null ? null : d(miles),
                revenue == null ? null : d(revenue), cost == null ? null : d(cost), appointment,
                appointment.minusSeconds(slackSeconds), hos == null ? null : d(hos));
    }
    private Score score(String deadhead, String cost, long slackSeconds, String hos) {
        return engine.score(input(deadhead, "100", "1000", cost, slackSeconds, hos), policy);
    }
    private void code(String expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(ApiException.class, operation).getCode());
    }

    @Test void confirmedPolicyContainsExactNamedWeightsAndNumericContract() {
        assertEquals("OPT_UTILITY_V1", policy.utilityPolicy().code());
        assertEquals("OPT_WEIGHT_V1", policy.weightPolicy().code());
        assertEquals("OPT_NUMERIC_V1", policy.numericPolicy().code());
        assertEquals(1, policy.numericPolicy().version());
        assertEquals(RoundingMode.HALF_EVEN, policy.numeric().roundingMode());
        assertEquals(34, policy.numeric().intermediatePrecision());
        assertEquals(d("0.350000"), policy.weights().get(Utility.MARGIN));
        assertEquals(d("1.000000"), policy.weights().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    @Test void everyExactKnotIsPreserved() {
        policy.curves().forEach((utility, knots) -> knots.forEach(knot ->
                assertEquals(knot.utility().setScale(6), engine.normalize(utility, knot.raw(), policy))));
    }
    @Test void linearInterpolationDoesNotUseCandidateSetNormalization() {
        assertEquals(d("0.800000"), engine.normalize(Utility.DEADHEAD, d(".175"), policy));
        assertEquals(d("0.650000"), engine.normalize(Utility.MARGIN, d(".15"), policy));
        assertEquals(d("0.650000"), engine.normalize(Utility.ON_TIME, d("90"), policy));
        assertEquals(d("0.650000"), engine.normalize(Utility.HOS, d(".15"), policy));
    }
    @Test void outsideBoundsClampToConfirmedEndpoints() {
        assertEquals(d("1.000000"), engine.normalize(Utility.DEADHEAD, d("-1"), policy));
        assertEquals(d("0.000000"), engine.normalize(Utility.DEADHEAD, d("2"), policy));
        for (Utility utility : List.of(Utility.MARGIN, Utility.ON_TIME, Utility.HOS)) {
            assertEquals(d("0.000000"), engine.normalize(utility, d("-1000"), policy));
            assertEquals(d("1.000000"), engine.normalize(utility, d("1000"), policy));
        }
    }
    @Test void authoritativeScoreReconcilesPersistedScaleEightContributions() {
        Score result = score("10", "800", 7200, ".2");
        assertEquals(d("0.82500000"), result.finalScore());
        assertEquals(result.finalScore(), result.components().values().stream()
                .map(OptimizationScoringEngine.ComponentResult::contribution).reduce(BigDecimal.ZERO, BigDecimal::add));
        result.components().values().forEach(c -> { assertEquals(6, c.normalizedUtility().scale());
            assertEquals(6, c.weight().scale()); assertEquals(8, c.contribution().scale()); });
    }
    @Test void halfEvenUtilityAndContributionBoundariesAreNotRatingHalfUp() {
        assertEquals(d("0.500000"), policy.numeric().utility(d("0.5000005")));
        assertEquals(d("0.500002"), policy.numeric().utility(d("0.5000015")));
        assertEquals(d("0.10000000"), policy.numeric().contribution(d("0.100000005")));
        assertEquals(d("0.10000002"), policy.numeric().contribution(d("0.100000015")));
    }
    @Test void fractionalMinutesAndNegativeSlackAreNotTruncated() {
        Inputs positive = new Inputs(true, d("1"), d("100"), d("100"), d("50"), appointment,
                appointment.minusMillis(30000), d(".1"));
        Score result = engine.score(positive, policy);
        assertEquals(0, d(".5").compareTo(result.components().get(Utility.ON_TIME).rawValue()));
        assertEquals(d("0.004167"), result.components().get(Utility.ON_TIME).normalizedUtility());
        Score late = engine.score(input("1", "100", "100", "50", -30, ".1"), policy);
        assertEquals(0, d("-.5").compareTo(late.components().get(Utility.ON_TIME).rawValue()));
        assertEquals(d("0.000000"), late.components().get(Utility.ON_TIME).normalizedUtility());
    }
    @Test void infeasibleCandidateIsRejectedBeforeAnyMissingInputScoring() {
        code("OPTIMIZATION_INFEASIBLE", () -> engine.score(new Inputs(false, null, null, null, null, null, null, null), policy));
    }
    @Test void loadedMilesMustBePositiveWithoutFallback() {
        for (String miles : new String[]{null, "0", "-1"}) {
            code("DEADHEAD_UTILITY_UNAVAILABLE", () -> engine.score(input("1", miles, "100", "50", 60, ".1"), policy));
        }
    }
    @Test void deadheadMustBeQualifiedNonnegative() {
        for (String miles : new String[]{null, "-1"})
            code("DEADHEAD_UTILITY_UNAVAILABLE", () -> engine.score(input(miles, "100", "100", "50", 60, ".1"), policy));
    }
    @Test void revenueMustBePositiveAcceptedAmount() {
        for (String revenue : new String[]{null, "0", "-1"})
            code("MARGIN_UTILITY_UNAVAILABLE", () -> engine.score(input("1", "100", revenue, "50", 60, ".1"), policy));
    }
    @Test void missingOrInvalidCostCannotBecomeZero() {
        for (String cost : new String[]{null, "-1"})
            code("FORECAST_COST_INCOMPLETE", () -> engine.score(input("1", "100", "100", cost, 60, ".1"), policy));
    }
    @Test void negativeContributionMarginIsValidButUtilityFloorsAtZero() {
        Score result = score("10", "1100", 7200, ".2");
        assertEquals(0, d("-.1").compareTo(result.components().get(Utility.MARGIN).rawValue()));
        assertEquals(d("0.000000"), result.components().get(Utility.MARGIN).normalizedUtility());
    }
    @Test void missingAppointmentOrForecastHasExplicitError() {
        code("ON_TIME_UTILITY_UNAVAILABLE", () -> engine.score(new Inputs(true, d("1"), d("10"), d("100"), d("50"),
                null, appointment, d(".1")), policy));
        code("ON_TIME_UTILITY_UNAVAILABLE", () -> engine.score(new Inputs(true, d("1"), d("10"), d("100"), d("50"),
                appointment, null, d(".1")), policy));
    }
    @Test void missingSimulatedHosHeadroomIsNotRemainingDrivingMinutes() {
        code("HOS_UTILITY_EVIDENCE_REQUIRED", () -> engine.score(input("1", "100", "100", "50", 60, null), policy));
    }
    @Test void policyCannotBeMissing() {
        code("INVALID_OPTIMIZATION_POLICY", () -> engine.score(input("1", "100", "100", "50", 60, ".1"), null));
    }
    @Test void missingNegativeAndWrongTotalWeightsAreRejected() {
        var missing = new EnumMap<Utility, BigDecimal>(policy.weights()); missing.remove(Utility.HOS);
        code("OPTIMIZATION_WEIGHT_POLICY_INVALID", () -> withWeights(missing));
        var negative = new EnumMap<Utility, BigDecimal>(policy.weights()); negative.put(Utility.HOS, d("-.1"));
        code("OPTIMIZATION_WEIGHT_POLICY_INVALID", () -> withWeights(negative));
        var wrong = new EnumMap<Utility, BigDecimal>(policy.weights()); wrong.put(Utility.HOS, d(".100001"));
        code("OPTIMIZATION_WEIGHT_POLICY_INVALID", () -> withWeights(wrong));
    }
    @Test void authoringDoesNotRoundWeightsToMakeTheirSumPass() {
        var weights = new EnumMap<Utility, BigDecimal>(policy.weights());
        weights.put(Utility.HOS, d(".1000001")); weights.put(Utility.MARGIN, d(".3499999"));
        code("OPTIMIZATION_WEIGHT_POLICY_INVALID", () -> withWeights(weights));
    }
    @Test void equalScoresHaveDenseRankWithoutUuidWinner() {
        Score best = score("10", "800", 7200, ".2"), lower = score("10", "900", 7200, ".2");
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001"), b = UUID.randomUUID(), c = UUID.randomUUID();
        var ranks = engine.denseRanks(List.of(new CandidateScore(c, lower), new CandidateScore(b, best), new CandidateScore(a, best)));
        assertEquals(List.of(1, 1, 2), ranks.stream().map(OptimizationScoringEngine.RankedCandidate::rank).toList());
        assertEquals(2, ranks.stream().filter(r -> r.rank() == 1).count());
    }
    @Test void candidateInsertionOrderDoesNotChangeRank() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var first = new CandidateScore(a, score("10", "800", 7200, ".2"));
        var second = new CandidateScore(b, score("10", "900", 7200, ".2"));
        assertEquals(engine.denseRanks(List.of(first, second)), engine.denseRanks(List.of(second, first)));
    }
    @Test void storedFinalScoreCannotDriftFromContributions() {
        Score result = score("10", "800", 7200, ".2");
        code("OPTIMIZATION_SCORE_INVALID", () -> new Score(policy, result.components(), d(".82500001")));
    }
    @Test void policyAndResultsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> policy.weights().put(Utility.HOS, d(".9")));
        assertThrows(UnsupportedOperationException.class, () -> policy.curves().get(Utility.HOS).clear());
        assertThrows(UnsupportedOperationException.class, () -> score("10", "800", 7200, ".2").components().clear());
    }
    private OptimizationScoringPolicy withWeights(java.util.Map<Utility, BigDecimal> weights) {
        return new OptimizationScoringPolicy(policy.utilityPolicy(), policy.weightPolicy(), policy.numericPolicy(),
                policy.curves(), weights, policy.numeric());
    }
}
