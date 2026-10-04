package com.company.logicstic.common;

import com.company.logicstic.exception.CurrencyMismatchException;
import com.company.logicstic.exception.BadRequestException;

public final class CurrencyGuard {

    private CurrencyGuard() {
    }

    public static void requireSameCurrency(String expectedCurrency, String actualCurrency) {
        String expected = normalize(expectedCurrency);
        String actual = normalize(actualCurrency);
        if (!expected.equals(actual)) {
            throw new CurrencyMismatchException(expected, actual);
        }
    }

    public static String normalize(String currency) {
        if (currency == null || !currency.trim().matches("[a-zA-Z]{3}")) {
            throw new BadRequestException("CURRENCY_INVALID", "An explicit three-letter currency code is required");
        }
        return currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public static String canonical(String currency) { return normalize(currency); }
}
