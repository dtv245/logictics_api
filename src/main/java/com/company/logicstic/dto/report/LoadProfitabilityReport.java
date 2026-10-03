package com.company.logicstic.dto.report;
import java.math.BigDecimal;
import java.util.UUID;
import com.company.logicstic.common.MetricDto;
public record LoadProfitabilityReport(UUID loadId, String currency, BigDecimal actualRevenue, BigDecimal actualCost,
        BigDecimal contributionMargin, BigDecimal allocatedProfit, MetricDto marginPercent,
        MetricDto revenuePerTotalMile, MetricDto costPerTotalMile, MetricDto breakEvenLoadedRate,
        BigDecimal estimatedCost, MetricDto costVariance, MetricDto contributionMarginMetric,
        MetricDto allocatedProfitMetric,
        com.company.logicstic.dto.profitability.CostClassificationSummary costClassification) {}
