package com.company.logicstic.service.optimization;

import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads qualified ESTIMATE/APPROVED facts; never creates or mutates accounting costs. */
@Component
public class OptimizationForecastResolver {
    private final OptimizationEvidenceValidator validator;
    public OptimizationForecastResolver(OptimizationEvidenceValidator validator) { this.validator = validator; }
    public enum Category { FUEL, DRIVER, TOLL, ACCESSORIAL, PERMIT }
    public record Revenue(UUID ratingSnapshotId, UUID loadId, String currency, BigDecimal acceptedSubtotalBeforeTax) {}
    public record ZeroCostEvidence(String reasonCode, String reason, UUID confirmedBy, Instant confirmedAt) {}
    public record Cost(UUID costId, long ledgerVersion, Category category, String costBasis, String status,
                       String currency, BigDecimal amount, UUID approvedBy, Instant approvedAt,
                       String forecastPolicyCode, int forecastPolicyVersion, ZeroCostEvidence zeroEvidence) {}
    public record Result(UUID ratingSnapshotId, List<UUID> forecastCostIds, Set<Category> requiredCategories,
                         List<Input<Cost>> costEvidence, String currency, BigDecimal expectedRevenue,
                         BigDecimal expectedVariableCost, BigDecimal expectedContributionMargin) {
        public Result { forecastCostIds = List.copyOf(forecastCostIds); requiredCategories = Set.copyOf(requiredCategories);
            costEvidence = List.copyOf(costEvidence); }
    }

    public Result resolve(CandidateContext context, Revenue revenue, List<Input<Cost>> inputs,
                          Map<Category, Boolean> conditionalApplicability, Policy policy, Instant evaluatedAt) {
        if (context == null || revenue == null || revenue.ratingSnapshotId() == null || !context.loadId().equals(revenue.loadId())
                || blank(revenue.currency()) || revenue.acceptedSubtotalBeforeTax() == null
                || revenue.acceptedSubtotalBeforeTax().signum() <= 0) throw fail("MARGIN_UTILITY_UNAVAILABLE", "Qualified accepted pre-tax revenue required");
        if (inputs == null || conditionalApplicability == null || conditionalApplicability.get(Category.ACCESSORIAL) == null
                || conditionalApplicability.get(Category.PERMIT) == null) throw incomplete();
        var required = new HashSet<>(Set.of(Category.FUEL, Category.DRIVER, Category.TOLL));
        for (Category category : List.of(Category.ACCESSORIAL, Category.PERMIT))
            if (conditionalApplicability.get(category)) required.add(category);
        var seenCategories = new HashSet<Category>();
        var seenIds = new HashSet<UUID>();
        var used = new ArrayList<Input<Cost>>();
        BigDecimal sum = BigDecimal.ZERO;
        for (Input<Cost> input : inputs) {
            if (!validator.validate(input, Kind.FORECAST_COST, context, policy, evaluatedAt).isEmpty()) throw incomplete();
            Cost cost = input.value();
            if (cost.costId() == null || !seenIds.add(cost.costId()) || cost.ledgerVersion() < 0 || cost.category() == null
                    || !required.contains(cost.category()) || !"ESTIMATE".equals(cost.costBasis()) || !"APPROVED".equals(cost.status())
                    || cost.amount() == null || cost.amount().signum() < 0 || cost.approvedBy() == null || cost.approvedAt() == null
                    || cost.approvedAt().isAfter(evaluatedAt) || blank(cost.forecastPolicyCode()) || cost.forecastPolicyVersion() <= 0) throw incomplete();
            if (!revenue.currency().equals(cost.currency()) || !revenue.currency().equals(input.provenance().unit())) {
                throw fail("OPTIMIZATION_CURRENCY_MISMATCH", "Accepted revenue and all candidate estimates must use one currency");
            }
            if (cost.amount().signum() == 0) {
                ZeroCostEvidence zero = cost.zeroEvidence();
                if (zero == null || !"ZERO_COST_CONFIRMED".equals(zero.reasonCode()) || blank(zero.reason())
                        || zero.confirmedBy() == null || zero.confirmedAt() == null || zero.confirmedAt().isAfter(evaluatedAt)) throw incomplete();
            }
            seenCategories.add(cost.category());
            used.add(input);
            sum = sum.add(cost.amount());
        }
        if (!seenCategories.containsAll(required)) throw incomplete();
        used.sort(java.util.Comparator.comparing(c -> c.value().costId()));
        return new Result(revenue.ratingSnapshotId(), used.stream().map(c -> c.value().costId()).toList(), required,
                used, revenue.currency(), revenue.acceptedSubtotalBeforeTax(), sum, revenue.acceptedSubtotalBeforeTax().subtract(sum));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static BadRequestException incomplete() { return fail("FORECAST_COST_INCOMPLETE", "Complete qualified candidate-specific approved variable forecasts and conditional applicability required"); }
    private static BadRequestException fail(String code, String message) { return new BadRequestException(code, message); }
}
