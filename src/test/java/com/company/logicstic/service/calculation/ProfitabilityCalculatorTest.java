package com.company.logicstic.service.calculation;

import com.company.logicstic.common.MetricAvailability;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.service.profitability.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProfitabilityCalculatorTest {
    final ProfitabilityCalculator calculator = new ProfitabilityCalculator(new DefaultCostClassificationPolicyV1(), TestRoundingPolicies.standard());
    ShipmentCost cost(String category, String source, String method, String amount) {
        ShipmentCost cost = new ShipmentCost(); cost.setId(UUID.randomUUID()); cost.setSourceId(UUID.randomUUID());
        cost.setCategory(category); cost.setSourceType(source); cost.setAllocationMethod(method); cost.setAmount(new BigDecimal(amount));
        cost.setCostBasis("ACTUAL"); cost.setStatus("POSTED"); cost.setCurrency("USD"); return cost;
    }
    @Test void reconcilesVariableFixedAndSignedCreditsWithExplainableCostIds() {
        var fuel = cost("FUEL","EXPENSE",null,"200");
        var direct = cost("MAINTENANCE","MANUAL","DIRECT","50");
        var maintenance = cost("MAINTENANCE","ALLOCATION","CPM_MILEAGE","100");
        var insurance = cost("INSURANCE","MANUAL",null,"25");
        var credit = cost("DRIVER","DRIVER_SETTLEMENT",null,"-20");
        var s = calculator.calculate(new BigDecimal("1000"),"USD",List.of(fuel,direct,maintenance,insurance,credit));
        assertEquals(new BigDecimal("230.00"),s.variableCost()); assertEquals(new BigDecimal("125.00"),s.allocatedFixedCost());
        assertEquals(new BigDecimal("770.00"),s.contributionMargin().value()); assertEquals(new BigDecimal("645.00"),s.allocatedProfit().value());
        assertEquals(new BigDecimal("0.770000"),s.contributionMarginPercent().value()); assertEquals(new BigDecimal("0.645000"),s.allocatedMarginPercent().value());
        assertEquals(new BigDecimal("0.00"),s.excludedCost()); assertEquals(new BigDecimal("0.00"),s.unclassifiedCost());
        assertEquals(fuel.getId(),s.costs().getFirst().costId()); assertEquals(fuel.getSourceId(),s.costs().getFirst().sourceId());
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(s));
        assertEquals("1",json.get("policyVersion").asText()); assertEquals("VARIABLE",json.get("costs").get(0).get("behavior").asText());
    }
    @Test void zeroRevenueKeepsMonetaryLossButSuppressesPercentages() {
        var s = calculator.calculate(BigDecimal.ZERO,"USD",List.of(cost("FUEL","MANUAL",null,"10")));
        assertEquals(new BigDecimal("-10.00"),s.allocatedProfit().value()); assertEquals(new BigDecimal("-10.00"),s.contributionMargin().value());
        assertNull(s.allocatedMarginPercent().value()); assertEquals("ZERO_REVENUE_DENOMINATOR",s.contributionMarginPercent().reason());
    }
    @Test void unknownCostsCannotPublishProfitEvenWhenUnknownCreditsNetToZero() {
        var s = calculator.calculate(new BigDecimal("100"),"USD",List.of(cost("FUEL","MANUAL",null,"10"),
                cost("OTHER","MANUAL",null,"5"),cost("OTHER","MANUAL",null,"-5")));
        assertEquals(new BigDecimal("10.00"),s.variableCost()); assertEquals(new BigDecimal("0.00"),s.unclassifiedCost());
        assertEquals(MetricAvailability.UNAVAILABLE,s.contributionMargin().availability()); assertNull(s.allocatedProfit().value());
        assertEquals(ProfitabilityCalculator.CLASSIFICATION_INCOMPLETE,s.allocatedMarginPercent().reason());
    }
    @Test void lifecycleAndBasisFiltersDoNotClassifyEstimatesOrDraftsAsActual() {
        var estimate = cost("MAINTENANCE","ALLOCATION","CPM_MILEAGE","500"); estimate.setCostBasis("ESTIMATE"); estimate.setCurrency("VND");
        var draft = cost("OTHER","MANUAL",null,"500"); draft.setStatus("DRAFT");
        var s = calculator.calculate(new BigDecimal("100"),"USD",List.of(estimate,draft));
        assertTrue(s.costs().isEmpty()); assertEquals(new BigDecimal("100.00"),s.allocatedProfit().value());
    }
    @Test void currencyGuardAlsoProtectsCalculatorAndGroupAggregation() {
        var foreign = cost("FUEL","MANUAL",null,"100"); foreign.setCurrency("VND");
        assertThrows(CurrencyMismatchException.class, () -> calculator.calculate(BigDecimal.TEN,"USD",List.of(foreign)));
        var summary = calculator.calculate(BigDecimal.TEN,"VND",List.of(foreign));
        assertThrows(CurrencyMismatchException.class, () -> calculator.aggregate("USD",List.of(summary)));
    }
    @Test void groupedPercentIsRevenueWeightedAndAnyUnknownLoadSuppressesGroupProfit() {
        var a = calculator.calculate(new BigDecimal("100"),"USD",List.of(cost("FUEL","MANUAL",null,"10")));
        var b = calculator.calculate(new BigDecimal("900"),"USD",List.of(cost("FUEL","MANUAL",null,"450")));
        var grouped = calculator.aggregate("USD",List.of(a,b));
        assertEquals(new BigDecimal("540.00"),grouped.allocatedProfit().value()); assertEquals(new BigDecimal("0.540000"),grouped.allocatedMarginPercent().value());
        var unknown = calculator.calculate(new BigDecimal("100"),"USD",List.of(cost("MAINTENANCE","EXPENSE",null,"10")));
        assertNull(calculator.aggregate("USD",List.of(a,unknown)).allocatedProfit().value());
    }
    @Test void roundsFinancialResultsOnceFromRawInputs() {
        var s = calculator.calculate(new BigDecimal("1"),"USD",List.of(cost("FUEL","MANUAL",null,"0.004"),cost("DRIVER","MANUAL",null,"0.004")));
        assertEquals(new BigDecimal("0.01"),s.variableCost()); assertEquals(new BigDecimal("0.99"),s.allocatedProfit().value());
        assertEquals(new BigDecimal("0.992000"),s.allocatedMarginPercent().value());
    }
    @Test void excludedActualCostsStayExplainableAndDoNotReduceProfit() {
        CostClassificationPolicy policy = new CostClassificationPolicy() {
            public String name() { return "TEST_EXCLUSION"; }
            public String version() { return "1"; }
            public Classification classify(Input input) { return new Classification(CostBehavior.EXCLUDED,"EXPLICIT_POLICY_EXCLUSION"); }
        };
        var s = new ProfitabilityCalculator(policy,TestRoundingPolicies.standard()).calculate(new BigDecimal("100"),"USD",
                List.of(cost("OTHER","MANUAL",null,"10")));
        assertEquals(new BigDecimal("10.00"),s.excludedCost()); assertEquals(new BigDecimal("100.00"),s.allocatedProfit().value());
        assertEquals(CostBehavior.EXCLUDED,s.costs().getFirst().behavior());
        assertThrows(com.company.logicstic.exception.BadRequestException.class, () -> calculator.aggregate("USD",List.of(s)));
    }
    @Test void currencyMismatchCannotHideInAnEmptyCostGroup() {
        var usd = calculator.calculate(new BigDecimal("100"),"USD",List.of());
        assertThrows(CurrencyMismatchException.class, () -> calculator.aggregate("VND",List.of(usd)));
    }
    @Test void unallocatedTripCostsSuppressLoadAndGroupProfitsWithoutProration() {
        var tripCost = cost("DRIVER","DRIVER_SETTLEMENT",null,"100"); var trip = new com.company.logicstic.entity.Trip(); trip.setId(UUID.randomUUID()); tripCost.setTrip(trip);
        var s = calculator.calculate(new BigDecimal("500"),"USD",List.of(),List.of(tripCost));
        assertEquals(new BigDecimal("0.00"),s.variableCost()); assertNull(s.allocatedProfit().value());
        assertEquals("TRIP_COST_ALLOCATION_REQUIRED",s.contributionMargin().reason()); assertEquals(trip.getId(),s.unallocatedTripCosts().getFirst().tripId());
        var group = calculator.aggregate("USD",List.of(s,s)); assertNull(group.allocatedProfit().value()); assertEquals(1,group.unallocatedTripCosts().size());
    }
}
