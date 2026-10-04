package com.company.logicstic.dto.report;

import java.math.BigDecimal;
import com.company.logicstic.common.MetricDto;

public record KnownOperatingCpmReport(
        BigDecimal knownOperatingCost,
        BigDecimal eligibleRecordedMiles,
        MetricDto knownOperatingCostPerMile,
        String currency,
        boolean driverCostIncluded,
        boolean fixedCostIncluded,
        boolean allocationComplete,
        BigDecimal expenseOperatingCost,
        BigDecimal maintenanceCost,
        MetricDto knownOperatingCostMetric,
        String maintenanceCurrencyReason
) {}
