package com.company.logicstic.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Versioned business-boundary rounding; deployment configuration is the source of truth. */
@Component
public class FinancialRoundingPolicy {
    public enum Boundary { INVOICE, REPORT, ALLOCATION }

    private final String version;
    private final RoundingMode invoice;
    private final RoundingMode report;
    private final RoundingMode allocation;

    public FinancialRoundingPolicy(
            @Value("${app.calculation.rounding.version}") String version,
            @Value("${app.calculation.rounding.invoice}") RoundingMode invoice,
            @Value("${app.calculation.rounding.report}") RoundingMode report,
            @Value("${app.calculation.rounding.allocation}") RoundingMode allocation) {
        if (version == null || version.isBlank() || invoice == null || report == null || allocation == null) {
            throw new IllegalArgumentException("A version and all rounding boundaries are required");
        }
        this.version = version;
        this.invoice = invoice;
        this.report = report;
        this.allocation = allocation;
    }

    public String version() { return version; }

    public RoundingMode mode(Boundary boundary) {
        return switch (boundary) {
            case INVOICE -> invoice;
            case REPORT -> report;
            case ALLOCATION -> allocation;
        };
    }

    public BigDecimal money(BigDecimal amount, String currency, Boundary boundary) {
        return amount == null ? null : amount.setScale(MoneyRoundingPolicy.getScaleForCurrency(currency), mode(boundary));
    }

    public BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale, Boundary boundary) {
        return numerator.divide(denominator, scale, mode(boundary));
    }
}
