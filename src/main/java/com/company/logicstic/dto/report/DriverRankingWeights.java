package com.company.logicstic.dto.report;

import java.math.BigDecimal;

public record DriverRankingWeights(
        BigDecimal onTimeWeight,
        BigDecimal productivityWeight,
        BigDecimal revenueWeight
) {
    public static final DriverRankingWeights DEFAULT = new DriverRankingWeights(
            new BigDecimal("0.4"),
            new BigDecimal("0.3"),
            new BigDecimal("0.3")
    );
}
