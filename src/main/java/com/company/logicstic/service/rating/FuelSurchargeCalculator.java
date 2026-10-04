package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.service.rating.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FuelSurchargeCalculator {
    private final RatingV1Rounding rounding;
    private final FuelIndexSelector indexes;
    public FuelSurchargeCalculator(RatingV1Rounding rounding, FuelIndexSelector indexes) {
        this.rounding = rounding; this.indexes = indexes;
    }
    public FuelSurchargeResult calculate(IndexBasedFscPolicy policy, LocalDate pricingDate,
            ResolvedRatingMileage mileage, String currency, List<FuelIndexObservation> observations) {
        if (policy == null) throw new BadRequestException("INVALID_RATE_POLICY", "An explicit FSC policy is required");
        if (policy.contractMpg() == null) throw new BadRequestException("RATE_MPG_REQUIRED", "Contract MPG required");
        if (policy.baseFuelPrice() == null) throw new BadRequestException("RATE_BASE_FUEL_PRICE_REQUIRED", "Base fuel price required");
        if (policy.contractMpg().signum() <= 0 || policy.baseFuelPrice().signum() < 0 || !"EIA".equals(policy.indexProvider())
                || !"USD".equals(policy.priceCurrency()) || !"GALLON".equals(policy.priceUnit()))
            throw new BadRequestException("INVALID_RATE_POLICY", "Invalid MPG, base fuel price, provider or unit");
        CurrencyGuard.requireSameCurrency("USD", currency);
        if (mileage == null || mileage.componentType() != RatingMileageComponent.FSC || policy.mileageBasis() == null
                || !policy.mileageBasis().name().equals(mileage.mileageBasis()) || mileage.eligibleMiles() == null
                || mileage.eligibleMiles().signum() < 0)
            throw new BadRequestException("RATING_VALIDATION_REQUIRED", "Explicit matching FSC mileage required");
        var index = indexes.select(policy, pricingDate, observations);
        BigDecimal difference = index.value().subtract(policy.baseFuelPrice(), RatingV1Rounding.INTERMEDIATE).max(BigDecimal.ZERO);
        BigDecimal rawUnit = difference.divide(policy.contractMpg(), RatingV1Rounding.INTERMEDIATE);
        BigDecimal unit = rounding.fuelUnitRate(rawUnit);
        BigDecimal rawTotal = unit.multiply(mileage.eligibleMiles(), RatingV1Rounding.INTERMEDIATE);
        return new FuelSurchargeResult(policy, index, mileage, rawUnit, unit, rawTotal, rounding.money(rawTotal, currency),
                CurrencyGuard.canonical(currency), RatingV1Rounding.CODE, RatingV1Rounding.VERSION);
    }
}
