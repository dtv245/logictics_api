package com.company.logicstic.dto.profitability;

import com.company.logicstic.common.MetricDto;
import com.company.logicstic.service.profitability.CostBehavior;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Auditable actual-cost inputs, policy identity and results, suitable for snapshot serialization. */
public record CostClassificationSummary(String policyName, String policyVersion, String currency, BigDecimal revenue,
        BigDecimal variableCost, BigDecimal allocatedFixedCost, BigDecimal excludedCost, BigDecimal unclassifiedCost,
        MetricDto contributionMargin, MetricDto contributionMarginPercent, MetricDto allocatedProfit,
        MetricDto allocatedMarginPercent, List<CostDetail> costs) {
    public record CostDetail(UUID costId, String category, String sourceType, UUID sourceId, String costBasis,
            String allocationMethod, BigDecimal amount, String currency, CostBehavior behavior, String reason) {}
}
