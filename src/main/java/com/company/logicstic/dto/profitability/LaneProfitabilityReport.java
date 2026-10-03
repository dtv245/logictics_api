package com.company.logicstic.dto.profitability;

import java.math.BigDecimal;
import com.company.logicstic.common.MetricDto;

public record LaneProfitabilityReport(
        String originState,
        String destinationState,
        String currency,
        long loadCount,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalProfit,
        BigDecimal averageMarginPercent,
        BigDecimal totalMiles,
        BigDecimal averageRpm,
        MetricDto totalMilesMetric,
        MetricDto averageRpmMetric,
        MetricDto totalProfitMetric,
        MetricDto averageMarginMetric,
        CostClassificationSummary costClassification
) {
    public LaneProfitabilityReport(String originState, String destinationState, String currency, long loadCount,
            BigDecimal totalRevenue, BigDecimal totalCost, BigDecimal totalProfit, BigDecimal averageMarginPercent,
            BigDecimal totalMiles, BigDecimal averageRpm) {
        this(originState, destinationState, currency, loadCount, totalRevenue, totalCost, totalProfit, averageMarginPercent,
                totalMiles, averageRpm, MetricDto.unavailable("TOTAL_MILES", "MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                MetricDto.unavailable("REVENUE_PER_MILE", "CURRENCY_PER_MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                MetricDto.unavailable("ALLOCATED_PROFIT", "CURRENCY", "LEGACY_SUMMARY_UNVERIFIED"),
                MetricDto.unavailable("MARGIN_PERCENT", "PERCENT", "LEGACY_SUMMARY_UNVERIFIED"), null);
    }
}
