package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import com.company.logicstic.common.MetricAvailability;

public record MonthlyFinancialSummary(
        int year,
        int month,
        BigDecimal totalRevenue,
        BigDecimal approvedExpenses,
        BigDecimal maintenanceCost,
        BigDecimal knownOperatingCost,
        String currency,
        MetricAvailability knownOperatingCostAvailability,
        String knownOperatingCostAvailabilityReason,
        String maintenanceCurrency,
        MetricAvailability maintenanceCurrencyAvailability,
        String maintenanceCurrencyReason
) {}
