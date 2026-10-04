package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.optimization.OptimizationForecastResolver.*;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationForecastResolverTest {
    final OptimizationEvidenceTest fixture = new OptimizationEvidenceTest();
    final OptimizationForecastResolver resolver = new OptimizationForecastResolver(fixture.validator);
    final Revenue revenue = new Revenue(UUID.randomUUID(), fixture.context.loadId(), "USD", new BigDecimal("1000.00"));
    final Map<Category, Boolean> applicability = Map.of(Category.ACCESSORIAL, false, Category.PERMIT, false);
    Input<Cost> cost(Category category, String amount) {
        return fixture.input(new Cost(UUID.randomUUID(), 1, category, "ESTIMATE", "APPROVED", "USD", new BigDecimal(amount), UUID.randomUUID(), fixture.now, "APPROVED_COST_POLICY", 1, null), "USD");
    }
    List<Input<Cost>> costs() { return List.of(cost(Category.FUEL, "100.00"), cost(Category.DRIVER, "200.00"), cost(Category.TOLL, "10.00")); }
    Result resolve(List<Input<Cost>> costs) { return resolver.resolve(fixture.context, revenue, costs, applicability, fixture.policy, fixture.now); }
    void code(String expected, org.junit.jupiter.api.function.Executable op) { assertEquals(expected, assertThrows(ApiException.class, op).getCode()); }
    @Test void contributionMarginUsesAcceptedPretaxSubtotalAndExactRequiredCosts() {
        Result result = resolve(costs()); assertEquals(new BigDecimal("310.00"), result.expectedVariableCost()); assertEquals(new BigDecimal("690.00"), result.expectedContributionMargin());
        assertEquals(revenue.ratingSnapshotId(), result.ratingSnapshotId()); assertEquals(3, result.forecastCostIds().size());
    }
    @Test void missingRequiredCostNeverBecomesZero() { code("FORECAST_COST_INCOMPLETE", () -> resolve(costs().subList(0, 2))); }
    @Test void conditionalApplicabilityMustBeExplicit() { code("FORECAST_COST_INCOMPLETE", () -> resolver.resolve(fixture.context, revenue, costs(), Map.of(), fixture.policy, fixture.now)); }
    @Test void applicablePermitAndAccessorialMustHaveQualifiedCosts() { code("FORECAST_COST_INCOMPLETE", () -> resolver.resolve(fixture.context, revenue, costs(), Map.of(Category.ACCESSORIAL, true, Category.PERMIT, true), fixture.policy, fixture.now)); }
    @Test void duplicateCostIdentityCannotDoubleCount() { var costs = costs(); code("FORECAST_COST_INCOMPLETE", () -> resolve(List.of(costs.get(0), costs.get(0), costs.get(1), costs.get(2)))); }
    @Test void zeroIsAcceptedOnlyWithExplicitAuditedEvidence() {
        Input<Cost> zero = cost(Category.TOLL, "0"); var first = costs(); code("FORECAST_COST_INCOMPLETE", () -> resolve(List.of(first.get(0), first.get(1), zero)));
        Cost c = zero.value(); Cost audited = new Cost(c.costId(), c.ledgerVersion(), c.category(), c.costBasis(), c.status(), c.currency(), c.amount(), c.approvedBy(), c.approvedAt(), c.forecastPolicyCode(), c.forecastPolicyVersion(), new ZeroCostEvidence("ZERO_COST_CONFIRMED", "Provider confirmed no toll", UUID.randomUUID(), fixture.now));
        assertEquals(new BigDecimal("300.00"), resolve(List.of(first.get(0), first.get(1), new Input<>(audited, zero.provenance()))).expectedVariableCost());
    }
    @Test void actualPostedDraftVoidOrUnapprovedCostsAreNotCandidateForecasts() {
        var first = costs(); Cost c = first.get(0).value();
        for (String status : List.of("DRAFT", "VERIFIED", "POSTED", "VOIDED")) {
            Cost invalid = new Cost(c.costId(), 1, c.category(), "ESTIMATE", status, c.currency(), c.amount(), c.approvedBy(), c.approvedAt(), c.forecastPolicyCode(), 1, null);
            code("FORECAST_COST_INCOMPLETE", () -> resolve(List.of(new Input<>(invalid, first.get(0).provenance()), first.get(1), first.get(2))));
        }
        Cost actual = new Cost(c.costId(), 1, c.category(), "ACTUAL", "APPROVED", c.currency(), c.amount(), c.approvedBy(), c.approvedAt(), c.forecastPolicyCode(), 1, null);
        code("FORECAST_COST_INCOMPLETE", () -> resolve(List.of(new Input<>(actual, first.get(0).provenance()), first.get(1), first.get(2))));
    }
    @Test void foreignCurrencyCannotUseImplicitFx() {
        var first = costs(); Cost c = first.get(0).value(); Cost eur = new Cost(c.costId(), 1, c.category(), c.costBasis(), c.status(), "EUR", c.amount(), c.approvedBy(), c.approvedAt(), c.forecastPolicyCode(), 1, null);
        code("OPTIMIZATION_CURRENCY_MISMATCH", () -> resolve(List.of(new Input<>(eur, first.get(0).provenance()), first.get(1), first.get(2))));
    }
    @Test void wrongLoadRevenueCannotBeUsed() { code("MARGIN_UTILITY_UNAVAILABLE", () -> resolver.resolve(fixture.context, new Revenue(revenue.ratingSnapshotId(), UUID.randomUUID(), "USD", revenue.acceptedSubtotalBeforeTax()), costs(), applicability, fixture.policy, fixture.now)); }
    @Test void negativeContributionMarginIsLegitimateNotFakeProfitableZero() { assertEquals(new BigDecimal("-110.00"), resolve(List.of(cost(Category.FUEL, "900.00"), cost(Category.DRIVER, "200.00"), cost(Category.TOLL, "10.00"))).expectedContributionMargin()); }
    @Test void foreignCandidateCostEvidenceIsRejected() {
        var first = costs(); Provenance p = first.get(0).provenance(); Provenance wrong = new Provenance(p.source(), new CandidateContext(fixture.context.loadId(), fixture.context.tripId(), UUID.randomUUID(), fixture.context.truckId(), fixture.now, fixture.context.planningEnd()), p.evidenceReference(), p.evidenceVersion(), p.unit(), p.observedAt(), p.expiresAt(), p.maxAgeSeconds());
        code("FORECAST_COST_INCOMPLETE", () -> resolve(List.of(new Input<>(first.get(0).value(), wrong), first.get(1), first.get(2))));
    }
    @Test void snapshotListsAndCostEvidenceAreImmutable() { var result = resolve(costs()); assertThrows(UnsupportedOperationException.class, () -> result.forecastCostIds().clear()); assertThrows(UnsupportedOperationException.class, () -> result.costEvidence().clear()); }
}
