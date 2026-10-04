package com.company.logicstic.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.dto.report.CustomerBalanceReport;
import com.company.logicstic.dto.report.DeliveryDelayReport;
import com.company.logicstic.dto.report.ExceptionSummaryReport;
import com.company.logicstic.dto.report.ExpenseSummaryReport;
import com.company.logicstic.dto.report.FuelReport;
import com.company.logicstic.dto.report.KnownOperatingCpmReport;
import com.company.logicstic.dto.report.LoadRevenueSummary;
import com.company.logicstic.dto.report.MaintenanceSummaryReport;
import com.company.logicstic.dto.report.MonthlyFinancialSummary;
import com.company.logicstic.dto.report.OtdReport;
import com.company.logicstic.dto.report.TransitTimeReport;
import com.company.logicstic.service.calculation.OperatingCostCalculator;
import com.company.logicstic.service.calculation.OperationsMetricsService;
import com.company.logicstic.service.calculation.RevenueCalculator;
import com.company.logicstic.service.calculation.TransitTimeCalculator;
import com.company.logicstic.service.profitability.ProfitabilityService;
import com.company.logicstic.dto.report.LoadProfitabilityReport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final RevenueCalculator revenueCalculator;
    private final TransitTimeCalculator transitTimeCalculator;
    private final OperationsMetricsService operationsMetricsService;
    private final OperatingCostCalculator operatingCostCalculator;
    private final ProfitabilityService profitabilityService;
    private final com.company.logicstic.service.fleet.FleetHistoryService fleetHistory;

    @GetMapping({"/reports/fleet/utilization-history", "/reports/fleet/health"})
    public ResponseEntity<ApiResponse<com.company.logicstic.service.fleet.FleetHistory.Report>> getFleetHistory(
            @RequestParam UUID policyId, @RequestParam java.util.List<UUID> truckIds,
            @RequestParam(required=false) java.time.Instant from, @RequestParam(required=false) java.time.Instant to,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) java.time.LocalDate firstDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) java.time.LocalDate exclusiveLastDate,
            @RequestParam(required=false) String businessZoneId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(fleetHistory.report(policyId,truckIds,from,to,firstDate,exclusiveLastDate,businessZoneId,request.getHeader("X-Correlation-Id")),request));
    }

    // --- TASK 1.1: REVENUE & BALANCE ---

    @GetMapping("/reports/revenue")
    public ResponseEntity<ApiResponse<LoadRevenueSummary>> getLoadRevenue(
            @RequestParam UUID loadId,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        LoadRevenueSummary summary = revenueCalculator.calculateLoadRevenue(loadId, currency);
        return ResponseEntity.ok(ApiResponse.success(summary, request));
    }

    @GetMapping("/customers/{customerId}/balance")
    public ResponseEntity<ApiResponse<CustomerBalanceReport>> getCustomerBalance(
            @PathVariable UUID customerId,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        CustomerBalanceReport report = revenueCalculator.calculateCustomerBalance(customerId, currency);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/reports/financials/monthly")
    public ResponseEntity<ApiResponse<MonthlyFinancialSummary>> getMonthlyFinancials(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        String reportCurrency = currency != null ? currency : "USD";
        OffsetDateTime from = OffsetDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = from.plusMonths(1).minusNanos(1);

        var rev = revenueCalculator.calculateRevenueForPeriod(from, to, reportCurrency);
        var exp = operatingCostCalculator.calculateApprovedExpenses(from, to, reportCurrency);
        var maint = operatingCostCalculator.calculateMaintenance(from, to, null, reportCurrency);
        // Maintenance currency is absent in the legacy schema; do not add unlike/unverified money values.
        var knownCost = (com.company.logicstic.common.MetricAvailability.AVAILABLE.equals(maint.currencyAvailability()))
                ? exp.totalApprovedAmount().add(maint.totalCost()) : null;

        MonthlyFinancialSummary summary = new MonthlyFinancialSummary(
                year,
                month,
                rev,
                exp.totalApprovedAmount(),
                maint.totalCost(),
                knownCost,
                reportCurrency,
                com.company.logicstic.common.MetricAvailability.PARTIAL,
                knownCost == null
                        ? "DUPLICATE_SOURCE_NOT_FULLY_RECONCILED;MAINTENANCE_CURRENCY_NOT_STORED"
                        : "DUPLICATE_SOURCE_NOT_FULLY_RECONCILED",
                maint.currency(),
                maint.currencyAvailability(),
                maint.currencyAvailabilityReason()
        );
        return ResponseEntity.ok(ApiResponse.success(summary, request));
    }

    // --- TASK 1.2: OPERATIONS & OTD ---

    @GetMapping("/reports/operations/on-time-delivery")
    public ResponseEntity<ApiResponse<OtdReport>> getOtdReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            HttpServletRequest request
    ) {
        OtdReport report = operationsMetricsService.calculateOtd(from, to);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/reports/operations/delays")
    public ResponseEntity<ApiResponse<DeliveryDelayReport>> getDelayReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            HttpServletRequest request
    ) {
        DeliveryDelayReport report = operationsMetricsService.calculateDelays(from, to);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/reports/operations/transit-time")
    public ResponseEntity<ApiResponse<TransitTimeReport>> getTransitTimeReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(transitTimeCalculator.calculate(from, to), request));
    }

    @GetMapping("/reports/operations/exceptions-summary")
    public ResponseEntity<ApiResponse<ExceptionSummaryReport>> getExceptionsSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            HttpServletRequest request
    ) {
        ExceptionSummaryReport report = operationsMetricsService.calculateExceptions(from, to);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    // --- TASK 1.3: COSTS & CPM ---

    @GetMapping("/reports/expenses")
    public ResponseEntity<ApiResponse<ExpenseSummaryReport>> getExpensesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        ExpenseSummaryReport report = operatingCostCalculator.calculateApprovedExpenses(from, to, currency);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/reports/fleet/fuel")
    public ResponseEntity<ApiResponse<FuelReport>> getFuelReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        FuelReport report = operatingCostCalculator.calculateFuelMetrics(from, to, currency);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/reports/fleet/maintenance")
    public ResponseEntity<ApiResponse<MaintenanceSummaryReport>> getMaintenanceReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID truckId,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        MaintenanceSummaryReport report = operatingCostCalculator.calculateMaintenance(from, to, truckId, currency);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/reports/costs/known-operating-cpm")
    public ResponseEntity<ApiResponse<KnownOperatingCpmReport>> getKnownOperatingCpm(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String currency,
            HttpServletRequest request
    ) {
        KnownOperatingCpmReport report = operatingCostCalculator.calculateKnownOperatingCpm(from, to, currency);
        return ResponseEntity.ok(ApiResponse.success(report, request));
    }

    @GetMapping("/loads/{loadId}/financial-summary")
    public ResponseEntity<ApiResponse<LoadProfitabilityReport>> getLoadProfitability(@PathVariable UUID loadId, @RequestParam(required = false) String currency, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(profitabilityService.byLoad(loadId, currency), request));
    }

    @GetMapping("/reports/profitability/by-load")
    public ResponseEntity<ApiResponse<java.util.List<com.company.logicstic.dto.profitability.LoadFinancialSummary>>> profitabilityByLoad(
            @RequestParam(required = false) UUID loadId, HttpServletRequest request) {
        var summaries = loadId == null ? profitabilityService.getAllLoadFinancialSummaries()
                : java.util.List.of(profitabilityService.getLoadFinancialSummary(loadId));
        return ResponseEntity.ok(ApiResponse.success(summaries, request));
    }

    @GetMapping("/reports/profitability/by-lane")
    public ResponseEntity<ApiResponse<java.util.List<com.company.logicstic.dto.profitability.LaneProfitabilityReport>>> profitabilityByLane(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(profitabilityService.getLaneProfitabilityReports(), request));
    }

    @GetMapping("/reports/profitability/by-truck")
    public ResponseEntity<ApiResponse<java.util.List<com.company.logicstic.dto.profitability.TruckProfitabilityReport>>> profitabilityByTruck(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(profitabilityService.getTruckProfitabilityReports(), request));
    }
}
