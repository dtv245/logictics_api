package com.company.logicstic.service.rating;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.exception.BadRequestException;
import java.util.Currency;
import org.springframework.stereotype.Component;

@Component
public class CurrencyScaleProvider {
    public int scale(String code) {
        String canonical = CurrencyGuard.canonical(code);
        try {
            int scale = Currency.getInstance(canonical).getDefaultFractionDigits();
            if (scale < 0) throw new IllegalArgumentException();
            return scale;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("RATING_CURRENCY_UNSUPPORTED", "Currency has no supported minor-unit scale");
        }
    }
}
