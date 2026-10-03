package com.company.logicstic.dto.profitability;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.dto.accessorial.AccessorialChargeView;
import com.company.logicstic.dto.cost.ShipmentCostView;
import com.company.logicstic.common.MetricDto;

public record LoadFinancialSummary(
        UUID loadId,
        String loadNumber,
        String currency,
        BigDecimal quotedRevenue,
        BigDecimal actualInvoicedRevenue,
        BigDecimal actualCost,
        BigDecimal estimatedCost,
        BigDecimal costVariance,
        BigDecimal contributionMargin,
        BigDecimal allocatedProfit,
        BigDecimal marginPercent,
        BigDecimal totalMiles,
        BigDecimal loadedMiles,
        BigDecimal emptyMiles,
        BigDecimal revenuePerTotalMile,
        BigDecimal costPerTotalMile,
        BigDecimal breakEvenLoadedRate,
        List<ShipmentCostView> costs,
        List<AccessorialChargeView> accessorials,
        ProfitabilityMileageMetrics mileageMetrics,
        MetricDto contributionMarginMetric,
        MetricDto allocatedProfitMetric,
        MetricDto marginPercentMetric,
        MetricDto costVarianceMetric,
        CostClassificationSummary costClassification
) {
    public LoadFinancialSummary(UUID loadId, String loadNumber, String currency, BigDecimal quotedRevenue,
            BigDecimal actualInvoicedRevenue, BigDecimal actualCost, BigDecimal estimatedCost, BigDecimal costVariance,
            BigDecimal contributionMargin, BigDecimal allocatedProfit, BigDecimal marginPercent, BigDecimal totalMiles,
            BigDecimal loadedMiles, BigDecimal emptyMiles, BigDecimal revenuePerTotalMile, BigDecimal costPerTotalMile,
            BigDecimal breakEvenLoadedRate, List<ShipmentCostView> costs, List<AccessorialChargeView> accessorials) {
        this(loadId, loadNumber, currency, quotedRevenue, actualInvoicedRevenue, actualCost, estimatedCost, costVariance,
                contributionMargin, allocatedProfit, marginPercent, totalMiles, loadedMiles, emptyMiles,
                revenuePerTotalMile, costPerTotalMile, breakEvenLoadedRate, costs, accessorials,
                new ProfitabilityMileageMetrics(MetricDto.unavailable("TOTAL_MILES", "MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                        MetricDto.unavailable("LOADED_MILES", "MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                        MetricDto.unavailable("EMPTY_MILES", "MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                        MetricDto.unavailable("REVENUE_PER_MILE", "CURRENCY_PER_MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                        MetricDto.unavailable("COST_PER_MILE", "CURRENCY_PER_MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED"),
                        MetricDto.unavailable("BREAK_EVEN_LOADED_RATE", "CURRENCY_PER_MILE", "EXPLICIT_LOAD_MILEAGE_NOT_PROVIDED")),
                MetricDto.unavailable("CONTRIBUTION_MARGIN", "CURRENCY", "LEGACY_SUMMARY_UNVERIFIED"),
                MetricDto.unavailable("ALLOCATED_PROFIT", "CURRENCY", "LEGACY_SUMMARY_UNVERIFIED"),
                MetricDto.unavailable("MARGIN_PERCENT", "PERCENT", "LEGACY_SUMMARY_UNVERIFIED"),
                MetricDto.unavailable("COST_VARIANCE", "CURRENCY", "LEGACY_SUMMARY_UNVERIFIED"), null);
    }
}
