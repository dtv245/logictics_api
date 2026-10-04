package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import com.company.logicstic.common.MetricAvailability;

public record MaintenanceSummaryReport(
        BigDecimal totalLaborCost,
        BigDecimal totalPartsCost,
        BigDecimal totalCost,
        String currency,
        int recordCount,
        MetricAvailability currencyAvailability,
        String currencyAvailabilityReason
) {}
