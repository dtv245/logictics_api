package com.company.logicstic.service.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.MetricDto;
import com.company.logicstic.common.MoneyRoundingPolicy;
import com.company.logicstic.common.FinancialRoundingPolicy;
import static com.company.logicstic.common.FinancialRoundingPolicy.Boundary.REPORT;
import com.company.logicstic.dto.report.ExpenseSummaryReport;
import com.company.logicstic.dto.report.FuelReport;
import com.company.logicstic.dto.report.KnownOperatingCpmReport;
import com.company.logicstic.dto.report.MaintenanceSummaryReport;
import com.company.logicstic.entity.Expense;
import com.company.logicstic.entity.MaintenanceRecord;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.repository.MaintenanceRecordRepository;
import com.company.logicstic.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperatingCostCalculator {

    private final ExpenseRepository expenseRepository;
    private final TripRepository tripRepository;
    private final ExpenseReportService expenseReportService;
    private final MaintenanceReportService maintenanceReportService;
    private final FinancialRoundingPolicy rounding;

    @Transactional(readOnly = true)
    public ExpenseSummaryReport calculateApprovedExpenses(OffsetDateTime from, OffsetDateTime to, String reportCurrency) {
        return expenseReportService.calculate(from, to, reportCurrency);
    }

    @Transactional(readOnly = true)
    public FuelReport calculateFuelMetrics(OffsetDateTime from, OffsetDateTime to, String reportCurrency) {
        String currency = CurrencyGuard.normalize(reportCurrency != null ? reportCurrency : "USD");
        List<Expense> fuelExpenses = expenseRepository.findApprovedExpenses("FUEL", from, to);

        BigDecimal totalFuelCost = BigDecimal.ZERO;
        BigDecimal totalGallons = BigDecimal.ZERO;
        boolean quantityComplete = true;

        for (Expense exp : fuelExpenses) {
            CurrencyGuard.requireSameCurrency(currency, exp.getAmountCurrency());
            if (exp.getAmountAmount() != null) {
                totalFuelCost = totalFuelCost.add(exp.getAmountAmount());
            }
            if (exp.getQuantity() != null && exp.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                String unit = exp.getQuantityUnit() != null ? exp.getQuantityUnit().toLowerCase() : "";
                if (java.util.Set.of("gal", "gallon", "gallons", "us_gallon", "us_gallons").contains(unit)) {
                    totalGallons = totalGallons.add(exp.getQuantity());
                } else if (java.util.Set.of("l", "liter", "liters", "litre", "litres").contains(unit)) {
                    BigDecimal convertedGallons = rounding.divide(exp.getQuantity(), new BigDecimal("3.785411784"), 6, REPORT);
                    totalGallons = totalGallons.add(convertedGallons);
                } else quantityComplete = false;
            } else quantityComplete = false;
        }

        BigDecimal avgCostPerGallon = null;
        if (quantityComplete && totalGallons.compareTo(BigDecimal.ZERO) > 0) {
            avgCostPerGallon = rounding.divide(totalFuelCost, totalGallons, 4, REPORT);
        }

        MetricDto mpgDto;
        if (quantityComplete && totalGallons.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalRecordedMiles = calculateExplicitActualMiles(from, to);
            if (totalRecordedMiles != null) {
                BigDecimal mpg = rounding.divide(totalRecordedMiles, totalGallons, 2, REPORT);
                mpgDto = MetricDto.available("MPG", mpg, "MILES_PER_GALLON", totalRecordedMiles, totalGallons, "TRIPS_ACTUAL_DISTANCE_MILES");
            } else {
                mpgDto = MetricDto.unavailable("MPG", "MILES_PER_GALLON", "NO_EXPLICIT_ACTUAL_MILES_FOUND_IN_PERIOD");
            }
        } else {
            mpgDto = MetricDto.unavailable("MPG", "MILES_PER_GALLON", quantityComplete ? "FUEL_QUANTITY_NOT_RECORDED" : "FUEL_QUANTITY_OR_UNIT_INCOMPLETE");
        }

        return new FuelReport(
                rounding.money(totalFuelCost, currency, REPORT),
                totalGallons.setScale(2, rounding.mode(REPORT)),
                avgCostPerGallon,
                currency,
                mpgDto
        );
    }

    @Transactional(readOnly = true)
    public MaintenanceSummaryReport calculateMaintenance(OffsetDateTime from, OffsetDateTime to, UUID truckId, String reportCurrency) {
        return maintenanceReportService.calculate(from, to, truckId, reportCurrency);
    }

    @Transactional(readOnly = true)
    public KnownOperatingCpmReport calculateKnownOperatingCpm(OffsetDateTime from, OffsetDateTime to, String reportCurrency) {
        String currency = CurrencyGuard.normalize(reportCurrency != null ? reportCurrency : "USD");

        ExpenseSummaryReport expenses = expenseReportService.calculateOperatingCost(from, to, currency);
        MaintenanceSummaryReport maintenance = calculateMaintenance(from, to, null, currency);

        BigDecimal knownCost = expenses.totalApprovedAmount();
        BigDecimal eligibleMiles = calculateExplicitActualMiles(from, to);

        MetricDto cpmMetric;
        if (eligibleMiles != null && eligibleMiles.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cpm = rounding.divide(knownCost, eligibleMiles, 4, REPORT);
            cpmMetric = MetricDto.partial(
                    "KNOWN_OPERATING_CPM",
                    cpm,
                    currency + "_PER_MILE",
                    knownCost,
                    eligibleMiles,
                    "TRIPS_ACTUAL_DISTANCE_MILES",
                    "MAINTENANCE_EXCLUDED_CURRENCY_NOT_STORED;DUPLICATE_SOURCE_NOT_FULLY_RECONCILED;EXCLUDES_DRIVER_COMPENSATION_AND_FIXED_COST_ALLOCATION"
            );
        } else {
            cpmMetric = MetricDto.unavailable("KNOWN_OPERATING_CPM", currency + "_PER_MILE", eligibleMiles == null ? "EXPLICIT_TRIP_MILEAGE_INCOMPLETE" : "ZERO_RECORDED_TRIP_MILES_IN_PERIOD");
        }

        return new KnownOperatingCpmReport(
                knownCost,
                eligibleMiles,
                cpmMetric,
                currency,
                false,
                false,
                false,
                expenses.totalApprovedAmount(), maintenance.totalCost(),
                MetricDto.partial("KNOWN_OPERATING_COST", knownCost, currency, "APPROVED_EXPENSES_ONLY",
                        "DUPLICATE_SOURCE_NOT_FULLY_RECONCILED"),
                maintenance.currencyAvailabilityReason()
        );
    }

    private BigDecimal calculateExplicitActualMiles(OffsetDateTime from, OffsetDateTime to) {
        List<Trip> trips = tripRepository.findAll();
        BigDecimal totalMiles = BigDecimal.ZERO;
        int eligibleCount = 0;
        boolean complete = true;
        for (Trip trip : trips) {
            if (trip.getDispatchedAt() == null) {
                continue;
            }
            if (from != null && trip.getDispatchedAt().isBefore(from)) {
                continue;
            }
            if (to != null && trip.getDispatchedAt().isAfter(to)) {
                continue;
            }
            eligibleCount++;
            if (trip.getActualDistanceMiles() != null && trip.getActualDistanceMiles().signum() >= 0) {
                totalMiles = totalMiles.add(trip.getActualDistanceMiles());
            } else complete = false;
        }
        return complete && eligibleCount > 0 ? totalMiles.setScale(3, rounding.mode(REPORT)) : null;
    }
}
