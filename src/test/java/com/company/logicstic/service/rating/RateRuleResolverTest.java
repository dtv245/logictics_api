package com.company.logicstic.service.rating;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.repository.RatePolicyRepository;
import com.company.logicstic.service.rating.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateRuleResolverTest {
    private final RatePolicyRepository policies = mock(RatePolicyRepository.class);
    private final RateRuleResolver resolver = new RateRuleResolver(policies);
    private final UUID customer = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 2, 1);
    private RateMatchContext context() { return new RateMatchContext(customer, null, null, "L1", "DRY_VAN", "EXPRESS", "T1", "USD", date); }
    private RateRule rule(int priority, UUID customer, UUID contract, Integer version, String lane, String equipment,
                          String service, String tier, LocalDate from, LocalDate to, String currency) {
        return new RateRule(UUID.randomUUID(), 1, priority, customer, contract, version, lane, equipment, service, tier,
                currency, from, to, RatingMethod.FLAT, BigDecimal.ONE, null, null, null, null,
                "RatingPolicyV1", 1, UUID.randomUUID(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }
    private RateRule generic(int priority) { return rule(priority, null, null, null, null, null, null, null, date, date, "USD"); }
    private String code(RateMatchContext c) { return assertThrows(BadRequestException.class, () -> resolver.resolve(c)).getCode(); }
    @BeforeEach void noRules() { when(policies.effectiveRules(date)).thenReturn(List.of()); }

    @Test void oneMatchingRule() {
        var r = generic(20); when(policies.effectiveRules(date)).thenReturn(List.of(r)); assertEquals(r, resolver.resolve(context()));
    }
    @Test void smallerPriorityWinsRegardlessOfDatabaseOrder() {
        var low = generic(Integer.MIN_VALUE); var high = generic(Integer.MAX_VALUE);
        for (var order : List.of(List.of(high, low), List.of(low, high))) {
            when(policies.effectiveRules(date)).thenReturn(order); assertEquals(low, resolver.resolve(context()));
        }
    }
    @Test void specificityCannotBeatExplicitPriority() {
        var general = generic(10); var specific = rule(20, customer, null, null, "L1", "DRY_VAN", "EXPRESS", "T1", date, date, "USD");
        when(policies.effectiveRules(date)).thenReturn(List.of(specific, general)); assertEquals(general, resolver.resolve(context()));
    }
    @Test void winningTieIsAmbiguousEvenWithDifferentSpecificity() {
        when(policies.effectiveRules(date)).thenReturn(List.of(generic(10), rule(10, customer, null, null, "L1", null, null, null, date, date, "USD")));
        assertEquals("RATE_RULE_AMBIGUOUS", code(context()));
    }
    @Test void lowerPrecedenceTieDoesNotMakeWinningRuleAmbiguous() {
        var winner = generic(10); when(policies.effectiveRules(date)).thenReturn(List.of(generic(20), winner, generic(20)));
        assertEquals(winner, resolver.resolve(context()));
    }
    @Test void allConfiguredDimensionsMustMatchAndFallbackNeedsItsOwnExplicitPriority() {
        var fallback = generic(100);
        for (var mismatch : List.of(
                rule(1, UUID.randomUUID(), null, null, null, null, null, null, date, date, "USD"),
                rule(1, customer, UUID.randomUUID(), 1, null, null, null, null, date, date, "USD"),
                rule(1, customer, null, null, "OTHER", null, null, null, date, date, "USD"),
                rule(1, customer, null, null, null, "REEFER", null, null, date, date, "USD"),
                rule(1, customer, null, null, null, null, "OTHER", null, date, date, "USD"),
                rule(1, customer, null, null, null, null, null, "OTHER", date, date, "USD"))) {
            when(policies.effectiveRules(date)).thenReturn(List.of(mismatch, fallback)); assertEquals(fallback, resolver.resolve(context()));
        }
    }
    @Test void configuredDimensionDoesNotMatchMissingRequestDimension() {
        when(policies.effectiveRules(date)).thenReturn(List.of(rule(1, customer, null, null, "L1", null, null, null, date, date, "USD")));
        assertEquals("RATE_RULE_NOT_FOUND", code(new RateMatchContext(customer, null, null, null, null, null, null, "USD", date)));
    }
    @Test void inclusiveBothEndpointsAndOpenEnd() {
        for (var interval : List.of(new LocalDate[] {date, date.plusDays(1)}, new LocalDate[] {date.minusDays(1), date}, new LocalDate[] {date, null})) {
            var r = rule(1, null, null, null, null, null, null, null, interval[0], interval[1], "USD");
            when(policies.effectiveRules(date)).thenReturn(List.of(r)); assertEquals(r, resolver.resolve(context()));
        }
    }
    @Test void expiredAndFutureRulesCannotMatch() {
        when(policies.effectiveRules(date)).thenReturn(List.of(
                rule(1, null, null, null, null, null, null, null, date.minusDays(2), date.minusDays(1), "USD"),
                rule(1, null, null, null, null, null, null, null, date.plusDays(1), null, "USD")));
        assertEquals("RATE_RULE_NOT_FOUND", code(context()));
    }
    @Test void noMatchHasDomainCode() { assertEquals("RATE_RULE_NOT_FOUND", code(context())); }
    @Test void missingBusinessDateNeverFallsBackToClock() {
        assertEquals("RATING_PRICING_DATE_REQUIRED", code(new RateMatchContext(customer, null, null, null, null, null, null, "USD", null)));
        assertEquals("RATING_PRICING_DATE_REQUIRED", code(null)); verifyNoInteractions(policies);
    }
    @Test void currencyMismatchDoesNotFallBackToLowerPriorityRule() {
        var incompatible = rule(1, null, null, null, null, null, null, null, date, date, "EUR");
        when(policies.effectiveRules(date)).thenReturn(List.of(incompatible, generic(100)));
        assertThrows(CurrencyMismatchException.class, () -> resolver.resolve(context()));
    }
    @Test void overlappingVersionsDoNotSilentlySelectNewest() {
        var r = generic(1); var v2 = new RateRule(r.ruleId(), 2, r.priority(), r.customerId(), r.contractId(), r.contractVersion(),
                r.lane(), r.equipment(), r.service(), r.tier(), r.currency(), r.effectiveFrom(), r.effectiveTo(), r.method(),
                r.baseRate(), r.linehaulMileageBasis(), r.minimumCharge(), r.maximumCharge(), r.fsc(), r.roundingPolicyCode(), 1, r.createdBy(), r.createdAt());
        when(policies.effectiveRules(date)).thenReturn(List.of(v2, r)); assertEquals("RATE_RULE_AMBIGUOUS", code(context()));
    }
    @Test void contractRequiresExactVersionCustomerCurrencyAndEffectiveDate() {
        UUID id = UUID.randomUUID(); var c = new RateMatchContext(customer, id, 1, "L1", null, null, null, "USD", date);
        var valid = new RatingContract(id, 1, customer, "USD", date, date, UUID.randomUUID(), OffsetDateTime.now());
        when(policies.contract(id, 1)).thenReturn(Optional.of(valid));
        var r = rule(10, customer, id, 1, "L1", null, null, null, date, date, "USD");
        when(policies.effectiveRules(date)).thenReturn(List.of(r)); assertEquals(r, resolver.resolve(c));
        when(policies.contract(id, 1)).thenReturn(Optional.of(new RatingContract(id, 1, UUID.randomUUID(), "USD", date, date, valid.createdBy(), valid.createdAt())));
        assertEquals("RATING_VALIDATION_REQUIRED", code(c));
        when(policies.contract(id, 1)).thenReturn(Optional.of(new RatingContract(id, 1, customer, "USD", date.plusDays(1), null, valid.createdBy(), valid.createdAt())));
        assertEquals("RATING_VALIDATION_REQUIRED", code(c));
        assertEquals("RATING_VALIDATION_REQUIRED", code(new RateMatchContext(customer, id, null, null, null, null, null, "USD", date)));
    }
}
