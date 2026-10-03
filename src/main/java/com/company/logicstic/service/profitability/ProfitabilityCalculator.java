package com.company.logicstic.service.profitability;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.common.MetricDto;
import com.company.logicstic.dto.profitability.CostClassificationSummary;
import com.company.logicstic.dto.profitability.CostClassificationSummary.CostDetail;
import com.company.logicstic.entity.ShipmentCost;
import com.company.logicstic.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component @RequiredArgsConstructor
public class ProfitabilityCalculator {
    public static final String CLASSIFICATION_INCOMPLETE = "COST_CLASSIFICATION_INCOMPLETE";
    private final CostClassificationPolicy policy;
    private final FinancialRoundingPolicy rounding;

    public CostClassificationSummary calculate(BigDecimal revenue, String currency, List<ShipmentCost> costs) {
        return calculate(revenue,currency,costs,List.of());
    }
    public CostClassificationSummary calculate(BigDecimal revenue, String currency, List<ShipmentCost> costs, List<ShipmentCost> unallocatedTripCosts) {
        return summarize(revenue,currency,details(currency,costs),details(currency,unallocatedTripCosts));
    }
    private List<CostDetail> details(String currency, List<ShipmentCost> costs) {
        return costs.stream().filter(c -> "ACTUAL".equalsIgnoreCase(c.getCostBasis()))
                .filter(c -> "APPROVED".equalsIgnoreCase(c.getStatus()) || "POSTED".equalsIgnoreCase(c.getStatus()))
                .map(c -> {
                    CurrencyGuard.requireSameCurrency(currency, c.getCurrency());
                    if (c.getAmount() == null) throw new BadRequestException("SHIPMENT_COST_AMOUNT_MISSING", "Eligible cost requires an amount");
                    var result = policy.classify(new CostClassificationPolicy.Input(c.getCategory(), c.getSourceType(),
                            c.getCostBasis(), c.getAllocationMethod()));
                    return new CostDetail(c.getId(), c.getTrip()==null?null:c.getTrip().getId(), c.getCategory(), c.getSourceType(), c.getSourceId(), c.getCostBasis(),
                            c.getAllocationMethod(), c.getAmount(), c.getCurrency(), result.behavior(), result.reason());
                }).toList();
    }

    public CostClassificationSummary aggregate(String currency, List<CostClassificationSummary> summaries) {
        for (var summary : summaries) {
            CurrencyGuard.requireSameCurrency(currency, summary.currency());
            if (!policy.name().equals(summary.policyName()) || !policy.version().equals(summary.policyVersion()))
                throw new BadRequestException("CLASSIFICATION_POLICY_MISMATCH", "Cannot aggregate different classification policies");
        }
        return summarize(summaries.stream().map(CostClassificationSummary::revenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                currency, summaries.stream().flatMap(s -> s.costs().stream()).toList(),
                summaries.stream().flatMap(s -> s.unallocatedTripCosts().stream()).distinct().toList());
    }

    private CostClassificationSummary summarize(BigDecimal revenue, String currency, List<CostDetail> costs, List<CostDetail> unallocatedTripCosts) {
        for (var cost : costs) CurrencyGuard.requireSameCurrency(currency, cost.currency());
        for (var cost : unallocatedTripCosts) CurrencyGuard.requireSameCurrency(currency,cost.currency());
        BigDecimal variable = sum(costs, CostBehavior.VARIABLE), fixed = sum(costs, CostBehavior.FIXED_ALLOCATABLE);
        boolean complete = unallocatedTripCosts.isEmpty() && costs.stream().noneMatch(c -> c.behavior() == CostBehavior.UNCLASSIFIED);
        String missing = unallocatedTripCosts.isEmpty() ? CLASSIFICATION_INCOMPLETE : "TRIP_COST_ALLOCATION_REQUIRED";
        String basis = policy.name() + ":" + policy.version();
        MetricDto contribution = complete ? MetricDto.available("CONTRIBUTION_MARGIN", money(revenue.subtract(variable), currency), "CURRENCY", basis)
                : MetricDto.unavailable("CONTRIBUTION_MARGIN", "CURRENCY", missing);
        MetricDto profit = complete ? MetricDto.available("ALLOCATED_PROFIT", money(revenue.subtract(variable).subtract(fixed), currency), "CURRENCY", basis)
                : MetricDto.unavailable("ALLOCATED_PROFIT", "CURRENCY", missing);
        return new CostClassificationSummary(policy.name(), policy.version(), CurrencyGuard.canonical(currency), revenue, money(variable, currency), money(fixed, currency),
                money(sum(costs, CostBehavior.EXCLUDED), currency), money(sum(costs, CostBehavior.UNCLASSIFIED), currency),
                contribution, percent("CONTRIBUTION_MARGIN_PERCENT", revenue.subtract(variable), revenue, complete, basis, missing), profit,
                percent("ALLOCATED_MARGIN_PERCENT", revenue.subtract(variable).subtract(fixed), revenue, complete, basis, missing), List.copyOf(costs),List.copyOf(unallocatedTripCosts));
    }

    private MetricDto percent(String code, BigDecimal numerator, BigDecimal revenue, boolean complete, String basis, String missing) {
        if (!complete) return MetricDto.unavailable(code, "RATIO", missing);
        if (revenue.signum() == 0) return MetricDto.unavailable(code, "RATIO", "ZERO_REVENUE_DENOMINATOR");
        return MetricDto.available(code, rounding.divide(numerator, revenue, 6,
                FinancialRoundingPolicy.Boundary.REPORT), "RATIO", numerator, revenue, basis);
    }
    private BigDecimal sum(List<CostDetail> costs, CostBehavior behavior) {
        return costs.stream().filter(c -> c.behavior() == behavior).map(CostDetail::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal money(BigDecimal amount, String currency) {
        return rounding.money(amount, currency, FinancialRoundingPolicy.Boundary.REPORT);
    }
}
