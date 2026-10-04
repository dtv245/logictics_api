package com.company.logicstic.service.calculation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import com.company.logicstic.common.*;
import com.company.logicstic.entity.*;
import com.company.logicstic.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhaseOneRegressionTest {
    @Mock LoadRepository loads;
    @Mock LoadExceptionRepository exceptions;
    @Mock ExpenseRepository expenses;
    @Mock MaintenanceRecordRepository maintenance;
    @Mock TripRepository trips;
    final FinancialRoundingPolicy rounding = TestRoundingPolicies.standard();

    @Test void lateDeliveryUnderOneMinuteIsStillLate() {
        Load load = new Load();
        load.setRequestedDeliveryDate(OffsetDateTime.parse("2026-10-01T12:00:00Z"));
        load.setDeliveredAt(OffsetDateTime.parse("2026-10-01T12:00:30Z"));
        when(loads.findAll()).thenReturn(List.of(load));
        var delay = new DelayCalculator(loads, rounding).calculate(null, null);
        assertEquals(1, delay.totalLateLoads());
        assertEquals(new BigDecimal("100.00"), delay.lateDeliveryPercentage());
        assertEquals(new BigDecimal("0.50"), delay.averageDelayMinutes());
        assertEquals(new BigDecimal("0.00"), new OnTimeCalculator(loads, rounding).calculate(null, null).otdPercentage());
    }

    @Test void emptyOperationsPopulationDoesNotFabricateZeroPerformance() {
        when(loads.findAll()).thenReturn(List.of());
        assertNull(new OnTimeCalculator(loads, rounding).calculate(null, null).otdPercentage());
        assertNull(new TransitTimeCalculator(loads, rounding).calculate(null, null).averageTransitMinutes());
        assertNull(new DelayCalculator(loads, rounding).calculate(null, null).lateDeliveryPercentage());
    }

    @Test void transitUsesInstantsAcrossOffsetsAndExcludesInvalidChronology() {
        Load valid = new Load();
        valid.setPickedUpAt(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        valid.setDeliveredAt(OffsetDateTime.parse("2026-10-01T09:00:00+07:00"));
        Load invalid = new Load(); invalid.setPickedUpAt(valid.getDeliveredAt()); invalid.setDeliveredAt(valid.getPickedUpAt());
        when(loads.findAll()).thenReturn(List.of(valid, invalid));
        var report = new TransitTimeCalculator(loads, rounding).calculate(null, null);
        assertEquals(1, report.eligibleLoadCount());
        assertEquals(new BigDecimal("120.00"), report.averageTransitMinutes());
    }

    @Test void invalidResolutionChronologyCannotDiluteMeasuredAverage() {
        LoadException valid = new LoadException();
        valid.setOccurredAt(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        valid.setResolvedAt(valid.getOccurredAt().plusMinutes(20));
        LoadException invalid = new LoadException(); invalid.setOccurredAt(valid.getOccurredAt()); invalid.setResolvedAt(valid.getOccurredAt().minusMinutes(1));
        when(exceptions.findByPeriod(null, null)).thenReturn(List.of(valid, invalid));
        var result = new ExceptionMetricsService(exceptions, rounding).calculate(null, null);
        assertEquals(2, result.resolvedExceptions());
        assertEquals(new BigDecimal("20.00"), result.averageResolutionMinutes());
    }

    @Test void fuelMetricsIgnoreLegacyMilesAndRejectIncompleteExplicitDenominator() {
        when(expenses.findApprovedExpenses("FUEL", null, null)).thenReturn(List.of(fuel()));
        Trip explicit = new Trip(); explicit.setDispatchedAt(OffsetDateTime.now()); explicit.setActualDistanceMiles(new BigDecimal("600"));
        Trip legacy = new Trip(); legacy.setDispatchedAt(OffsetDateTime.now()); legacy.setTotalDistance(10000.0);
        when(trips.findAll()).thenReturn(List.of(explicit, legacy));
        var result = costs().calculateFuelMetrics(null, null, "USD");
        assertEquals(MetricAvailability.UNAVAILABLE, result.mpg().availability());
        assertNull(result.mpg().value());
    }

    @Test void incompleteFuelQuantityCannotYieldACostPerGallonOrMpg() {
        Expense missing = fuel(); missing.setQuantity(null);
        when(expenses.findApprovedExpenses("FUEL", null, null)).thenReturn(List.of(fuel(), missing));
        var result = costs().calculateFuelMetrics(null, null, "USD");
        assertNull(result.averageCostPerGallon());
        assertNull(result.mpg().value());
        assertEquals("FUEL_QUANTITY_OR_UNIT_INCOMPLETE", result.mpg().reason());
    }

    @Test void costsRemainSeparateAndDriverCompensationIsNotIncludedInOperatingCost() {
        Expense fuel = fuel(); fuel.setAmountAmount(new BigDecimal("40"));
        Expense repair = fuel(); repair.setCategory("MAINTENANCE"); repair.setAmountAmount(new BigDecimal("60"));
        Expense driver = fuel(); driver.setCategory("DRIVER"); driver.setAmountAmount(new BigDecimal("20"));
        when(expenses.findApprovedExpenses(null, null, null)).thenReturn(List.of(fuel, repair, driver));
        MaintenanceRecord record = new MaintenanceRecord(); record.setLaborCost(BigDecimal.TEN); record.setPartsCost(new BigDecimal("20")); record.setTotalCost(new BigDecimal("999"));
        when(maintenance.findByFilters(null, null, null)).thenReturn(List.of(record));
        Trip trip = new Trip(); trip.setDispatchedAt(OffsetDateTime.now()); trip.setActualDistanceMiles(new BigDecimal("100"));
        when(trips.findAll()).thenReturn(List.of(trip));
        var result = costs().calculateKnownOperatingCpm(null, null, "USD");
        assertEquals(new BigDecimal("100.00"), result.expenseOperatingCost());
        assertEquals(new BigDecimal("30"), result.maintenanceCost());
        assertFalse(result.driverCostIncluded());
        assertEquals(MetricAvailability.PARTIAL, result.knownOperatingCostMetric().availability());
        assertEquals("DUPLICATE_SOURCE_NOT_FULLY_RECONCILED", result.knownOperatingCostMetric().reason());
    }

    @Test void roundingPolicyResolvesBusinessBoundariesAndCurrencyScale() {
        var policy = new FinancialRoundingPolicy("V2", RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN);
        assertEquals(new BigDecimal("1.01"), policy.money(new BigDecimal("1.005"), "USD", FinancialRoundingPolicy.Boundary.INVOICE));
        assertEquals(new BigDecimal("1.00"), policy.money(new BigDecimal("1.005"), "USD", FinancialRoundingPolicy.Boundary.REPORT));
        assertEquals(new BigDecimal("1"), policy.money(new BigDecimal("1.9"), "VND", FinancialRoundingPolicy.Boundary.ALLOCATION));
    }

    private OperatingCostCalculator costs() {
        return new OperatingCostCalculator(expenses, trips, new ExpenseReportService(expenses, rounding), new MaintenanceReportService(maintenance), rounding);
    }
    private Expense fuel() {
        Expense fuel = new Expense(); fuel.setCategory("FUEL"); fuel.setAmountAmount(new BigDecimal("100")); fuel.setAmountCurrency("USD"); fuel.setQuantity(BigDecimal.TEN); fuel.setQuantityUnit("gallons");
        return fuel;
    }
}
