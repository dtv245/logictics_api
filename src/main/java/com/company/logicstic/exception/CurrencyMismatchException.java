package com.company.logicstic.exception;

import org.springframework.http.HttpStatus;

public class CurrencyMismatchException extends ApiException {

    public CurrencyMismatchException(String expectedCurrency, String actualCurrency) {
        super(
                HttpStatus.BAD_REQUEST,
                "CURRENCY_MISMATCH",
                String.format("Cannot perform calculation across different currencies: expected '%s', encountered '%s'", expectedCurrency, actualCurrency)
        );
    }
}
