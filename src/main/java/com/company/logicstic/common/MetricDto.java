package com.company.logicstic.common;

import java.math.BigDecimal;

public record MetricDto(
        String code,
        BigDecimal value,
        String unit,
        BigDecimal numerator,
        BigDecimal denominator,
        MetricAvailability availability,
        String basis,
        String reason
) {
    public static MetricDto available(String code, BigDecimal value, String unit, String basis) {
        return new MetricDto(code, value, unit, null, null, MetricAvailability.AVAILABLE, basis, null);
    }

    public static MetricDto available(String code, BigDecimal value, String unit, BigDecimal numerator, BigDecimal denominator, String basis) {
        return new MetricDto(code, value, unit, numerator, denominator, MetricAvailability.AVAILABLE, basis, null);
    }

    public static MetricDto partial(String code, BigDecimal value, String unit, String basis, String reason) {
        return new MetricDto(code, value, unit, null, null, MetricAvailability.PARTIAL, basis, reason);
    }

    public static MetricDto partial(String code, BigDecimal value, String unit, BigDecimal numerator, BigDecimal denominator, String basis, String reason) {
        return new MetricDto(code, value, unit, numerator, denominator, MetricAvailability.PARTIAL, basis, reason);
    }

    public static MetricDto unavailable(String code, String unit, String reason) {
        return new MetricDto(code, null, unit, null, null, MetricAvailability.UNAVAILABLE, null, reason);
    }

    public static MetricDto notApplicable(String code, String reason) {
        return new MetricDto(code, null, null, null, null, MetricAvailability.NOT_APPLICABLE, null, reason);
    }
}
