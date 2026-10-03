package com.company.logicstic.dto.profitability;

import java.math.BigDecimal;
import java.util.UUID;
import com.company.logicstic.common.MetricDto;

public record TruckProfitabilityReport(
        UUID truckId,
        String truckNumber,
        String currency,
        long loadCount,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalProfit,
        BigDecimal averageMarginPercent,
        BigDecimal totalMiles,
        BigDecimal costPerMile,
        MetricDto totalMilesMetric,
        MetricDto costPerMileMetric,
        MetricDto totalProfitMetric,
        MetricDto averageMarginMetric,
        MetricDto truckAttributionMetric,
        CostClassificationSummary costClassification
) {
    public TruckProfitabilityReport(UUID truckId, String truckNumber, String currency, long loadCount, BigDecimal totalRevenue,
            BigDecimal totalCost, BigDecimal totalProfit, BigDecimal averageMarginPercent, BigDecimal totalMiles, BigDecimal costPerMile) {
        this(truckId, truckNumber, currency, loadCount, totalRevenue, totalCost, totalProfit, averageMarginPercent, totalMiles,
                costPerMile, MetricDto.unavailable("TOTAL_MILES", "MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                MetricDto.unavailable("COST_PER_MILE", "CURRENCY_PER_MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                MetricDto.unavailable("ALLOCATED_PROFIT", "CURRENCY", "LEGACY_SUMMARY_UNVERIFIED"),
                MetricDto.unavailable("MARGIN_PERCENT", "PERCENT", "LEGACY_SUMMARY_UNVERIFIED"),
                MetricDto.unavailable("TRUCK_ATTRIBUTION", null, "LEGACY_SUMMARY_UNVERIFIED"), null);
    }
}
