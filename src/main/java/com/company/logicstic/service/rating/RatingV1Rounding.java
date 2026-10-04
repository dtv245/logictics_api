package com.company.logicstic.service.rating;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Explicit RatingPolicyV1, not a change to payroll/reporting rounding. */
@Component
public class RatingV1Rounding {
    public static final String CODE = "RatingPolicyV1";
    public static final int VERSION = 1;
    public static final MathContext INTERMEDIATE = MathContext.DECIMAL128;
    private final CurrencyScaleProvider currencies;
    public RatingV1Rounding(CurrencyScaleProvider currencies) { this.currencies = currencies; }
    public BigDecimal money(BigDecimal raw, String currency) {
        return raw.setScale(currencies.scale(currency), RoundingMode.HALF_UP);
    }
    public BigDecimal fuelUnitRate(BigDecimal raw) { return raw.setScale(6, RoundingMode.HALF_UP); }
    public BigDecimal boundedMoney(BigDecimal raw, BigDecimal minimum, BigDecimal maximum, String currency) {
        BigDecimal bounded = minimum == null ? raw : raw.max(minimum);
        if (maximum != null) bounded = bounded.min(maximum);
        return money(bounded, currency);
    }
}
