package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.dto.rating.RateRuleRequest;
import com.company.logicstic.dto.rating.RatingContractRequest;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.RatingMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public final class RatePolicyValidator {
    private RatePolicyValidator() { }

    public static void contract(RatingContractRequest r) {
        require(r != null && r.customerId() != null, "A customer is required for a contract");
        CurrencyGuard.canonical(r.currency());
        dates(r.effectiveFrom(), r.effectiveTo());
    }

    public static void rule(RateRuleRequest r) {
        require(r != null && r.priority() != null && r.method() != null, "Explicit priority and method are required");
        String currency = CurrencyGuard.canonical(r.currency());
        dates(r.effectiveFrom(), r.effectiveTo());
        require((r.contractId() == null) == (r.contractVersion() == null), "Contract identity requires its version");
        require(r.contractVersion() == null || r.contractVersion() > 0, "Contract version must be positive");
        for (String d : new String[] {r.lane(), r.equipment(), r.service(), r.tier()})
            require(d == null || !d.isBlank(), "Configured dimensions cannot be blank");
        require(r.lane() == null || r.lane().length() <= 200, "Lane exceeds storage length");
        for (String d : new String[] {r.equipment(), r.service(), r.tier()})
            require(d == null || d.length() <= 80, "Dimension exceeds storage length");
        require(r.baseRate() != null, "Base rate is required");
        nonnegative(r.baseRate()); nonnegative(r.minimumCharge()); nonnegative(r.maximumCharge());
        require(r.minimumCharge() == null || r.maximumCharge() == null
                || r.maximumCharge().compareTo(r.minimumCharge()) >= 0, "Maximum cannot be below minimum");
        require(r.method() == RatingMethod.PER_MILE ? r.linehaulMileageBasis() != null
                : r.linehaulMileageBasis() == null, "PER_MILE needs an explicit basis; FLAT is non-mileage");
        var f = r.fsc();
        if (f == null) return; // No configured FSC, not a default zero-valued fuel policy.
        if (f.contractMpg() == null) throw new BadRequestException("RATE_MPG_REQUIRED", "Contract MPG is required");
        if (f.baseFuelPrice() == null) throw new BadRequestException("RATE_BASE_FUEL_PRICE_REQUIRED", "Base fuel price is required");
        require(f.contractMpg().signum() > 0 && f.baseFuelPrice().signum() >= 0, "Invalid contract MPG/base fuel price");
        require(f.mileageBasis() != null && "EIA".equals(f.indexProvider()), "Explicit mileage and supported index provider required");
        require(f.indexRegion() != null && Set.of("US", "PADD1", "PADD1A", "PADD1B", "PADD1C",
                "PADD2", "PADD3", "PADD4", "PADD5", "CALIFORNIA").contains(f.indexRegion()), "Unsupported EIA region");
        require(f.maxIndexAgeDays() != null && f.maxIndexAgeDays() >= 0, "Explicit nonnegative maximum index age required");
        require("USD".equals(currency) && "USD".equals(f.priceCurrency()) && "GALLON".equals(f.priceUnit()),
                "EIA index-based V1 policy requires compatible USD/gallon units");
    }

    private static void dates(LocalDate from, LocalDate to) {
        require(from != null && (to == null || !to.isBefore(from)), "Invalid inclusive effective dates");
    }
    private static void nonnegative(BigDecimal v) { require(v == null || v.signum() >= 0, "Negative rate/charge"); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new BadRequestException("INVALID_RATE_POLICY", message);
    }
}
