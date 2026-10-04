package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.RatePolicyRepository;
import com.company.logicstic.service.rating.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class RateRuleResolver {
    private final RatePolicyRepository policies;

    public RateRule resolve(RateMatchContext context) {
        if (context == null || context.pricingDate() == null)
            throw new BadRequestException("RATING_PRICING_DATE_REQUIRED", "Load requested pickup business LocalDate is required");
        if (context.customerId() == null)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Load customer is required");
        String currency = CurrencyGuard.canonical(context.currency());
        validateContract(context, currency);
        var candidates = policies.effectiveRules(context.pricingDate()).stream()
                // Keep explicit date checks even when a future repository implementation changes.
                .filter(r -> !context.pricingDate().isBefore(r.effectiveFrom())
                        && (r.effectiveTo() == null || !context.pricingDate().isAfter(r.effectiveTo())))
                .filter(r -> matches(r.customerId(), context.customerId()) && matches(r.contractId(), context.contractId())
                        && matches(r.lane(), context.lane()) && matches(r.equipment(), context.equipment())
                        && matches(r.service(), context.service()) && matches(r.tier(), context.tier()))
                .toList();
        int priority = candidates.stream().min(Comparator.comparingInt(RateRule::priority)).map(RateRule::priority)
                .orElseThrow(() -> new BadRequestException("RATE_RULE_NOT_FOUND", "No effective matching rate rule"));
        var winners = candidates.stream().filter(r -> r.priority() == priority).toList();
        if (winners.size() != 1)
            throw new BadRequestException("RATE_RULE_AMBIGUOUS", "Multiple rules match at the winning explicit priority " + priority);
        var winner = winners.getFirst();
        // Currency is a financial validation, never a hidden lower-priority fallback.
        CurrencyGuard.requireSameCurrency(currency, winner.currency());
        if (winner.contractId() != null && !Objects.equals(winner.contractVersion(), context.contractVersion()))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Winning rule pins another contract version");
        return winner;
    }

    private void validateContract(RateMatchContext c, String currency) {
        if ((c.contractId() == null) != (c.contractVersion() == null) || (c.contractVersion() != null && c.contractVersion() <= 0))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Contract identity and positive version must be explicit together");
        if (c.contractId() == null) return;
        var contract = policies.contract(c.contractId(), c.contractVersion())
                .orElseThrow(() -> new BadRequestException("RATING_VALIDATION_REQUIRED", "Contract version is unavailable"));
        CurrencyGuard.requireSameCurrency(currency, contract.currency());
        if (!contract.customerId().equals(c.customerId()) || c.pricingDate().isBefore(contract.effectiveFrom())
                || (contract.effectiveTo() != null && c.pricingDate().isAfter(contract.effectiveTo())))
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Contract is not valid for Load customer/pricingDate");
    }
    private boolean matches(Object dimension, Object actual) { return dimension == null || dimension.equals(actual); }
}
