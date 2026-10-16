package com.company.logicstic.controller;

import com.company.logicstic.dto.report.*;
import com.company.logicstic.exception.GlobalExceptionHandler;
import com.company.logicstic.service.calculation.*;
import com.company.logicstic.service.fleet.FleetHistoryService;
import com.company.logicstic.service.profitability.ProfitabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock private RevenueCalculator revenueCalculator;
    @Mock private TransitTimeCalculator transitTimeCalculator;
    @Mock private OperationsMetricsService operationsMetricsService;
    @Mock private OperatingCostCalculator operatingCostCalculator;
    @Mock private ProfitabilityService profitabilityService;
    @Mock private FleetHistoryService fleetHistory;
    @Mock private DriverRankingService driverRankingService;

    @BeforeEach
    void setUp() {
        ReportController controller = new ReportController(
                revenueCalculator,
                transitTimeCalculator,
                operationsMetricsService,
                operatingCostCalculator,
                profitabilityService,
                fleetHistory,
                driverRankingService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/reports/operations/on-time-delivery with valid YYYY-MM-DD returns 200")
    void testOtdValidDate() throws Exception {
        OtdReport report = new OtdReport(10, 9, new BigDecimal("90.00"), new BigDecimal("15.00"));
        when(operationsMetricsService.calculateOtd(eq(LocalDate.of(2026, 9, 16)), eq(LocalDate.of(2026, 10, 16))))
                .thenReturn(report);

        mockMvc.perform(get("/api/reports/operations/on-time-delivery")
                        .param("from", "2026-09-16")
                        .param("to", "2026-10-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.otdPercentage").value(90.00));
    }

    @Test
    @DisplayName("GET /api/reports/expenses with valid YYYY-MM-DD returns 200")
    void testExpensesValidDate() throws Exception {
        ExpenseSummaryReport report = new ExpenseSummaryReport(new BigDecimal("1500.00"), "USD", Map.of("FUEL", new BigDecimal("1500.00")), 5);
        when(operatingCostCalculator.calculateApprovedExpenses(eq(LocalDate.of(2026, 9, 16)), eq(LocalDate.of(2026, 10, 16)), eq("USD")))
                .thenReturn(report);

        mockMvc.perform(get("/api/reports/expenses")
                        .param("from", "2026-09-16")
                        .param("to", "2026-10-16")
                        .param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalApprovedAmount").value(1500.00));
    }

    @Test
    @DisplayName("GET /api/reports/drivers/ranking with valid parameters returns 200")
    void testDriverRankingValid() throws Exception {
        DriverRankingItem item = new DriverRankingItem(
                "d-1", "Alice", null, 1, new BigDecimal("95.5"), 10,
                new BigDecimal("2500.0"), new BigDecimal("98.0"), new BigDecimal("8000.00"),
                new BigDecimal("2400.00"), new BigDecimal("3.20"), 0, false
        );
        DriverRankingReport report = new DriverRankingReport(
                new ReportPeriodDto("2026-09-16", "2026-10-16"),
                "USD",
                "score",
                List.of(item),
                DriverRankingWeights.DEFAULT,
                false
        );
        when(driverRankingService.getRanking(eq(LocalDate.of(2026, 9, 16)), eq(LocalDate.of(2026, 10, 16)), eq("USD"), eq("score"), eq(5)))
                .thenReturn(report);

        mockMvc.perform(get("/api/reports/drivers/ranking")
                        .param("from", "2026-09-16")
                        .param("to", "2026-10-16")
                        .param("currency", "USD")
                        .param("sortBy", "score")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.drivers[0].driverName").value("Alice"))
                .andExpect(jsonPath("$.data.drivers[0].rank").value(1))
                .andExpect(jsonPath("$.data.weights.onTimeWeight").value(0.4));
    }

    @Test
    @DisplayName("Invalid date format returns 400 MALFORMED_REQUEST with parameter, value, and expectedType")
    void testInvalidDateFormatReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/operations/on-time-delivery")
                        .param("from", "invalid-date-string")
                        .param("to", "2026-10-16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request parameter 'from' with value 'invalid-date-string' is invalid; expected type 'LocalDate'"))
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].params.expectedType").value("LocalDate"));
    }

    @Test
    @DisplayName("Invalid date range (from > to) returns 400 INVALID_REPORT_PERIOD")
    void testInvalidDateRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/operations/on-time-delivery")
                        .param("from", "2026-10-20")
                        .param("to", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"))
                .andExpect(jsonPath("$.message").value("'from' date must be before or equal to 'to' date"))
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_RANGE"));
    }

    @Test
    @DisplayName("GET /api/reports/financials/monthly returns 200 with zero amounts when no data exists")
    void testMonthlyFinancialsEmptyReturns200() throws Exception {
        when(revenueCalculator.calculateRevenueForPeriod(any(), any(), eq("USD")))
                .thenReturn(BigDecimal.ZERO);
        when(operatingCostCalculator.calculateApprovedExpenses(any(java.time.OffsetDateTime.class), any(java.time.OffsetDateTime.class), eq("USD")))
                .thenReturn(new ExpenseSummaryReport(BigDecimal.ZERO, "USD", Map.of(), 0));
        when(operatingCostCalculator.calculateMaintenance(any(java.time.OffsetDateTime.class), any(java.time.OffsetDateTime.class), isNull(), eq("USD")))
                .thenReturn(new MaintenanceSummaryReport(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, 0,
                        com.company.logicstic.common.MetricAvailability.PARTIAL, "MAINTENANCE_CURRENCY_NOT_STORED_IN_LEGACY_SCHEMA"));

        mockMvc.perform(get("/api/reports/financials/monthly")
                        .param("year", "2026")
                        .param("month", "6")
                        .param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(6))
                .andExpect(jsonPath("$.data.totalRevenue").value(0))
                .andExpect(jsonPath("$.data.approvedExpenses").value(0))
                .andExpect(jsonPath("$.data.maintenanceCost").value(0));
    }
}
