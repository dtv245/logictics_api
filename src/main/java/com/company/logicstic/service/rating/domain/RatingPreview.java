package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Ephemeral explainable rating; subtotal excludes separately assessed tax. */
public record RatingPreview(RatingInputs inputs, BigDecimal rawLinehaul, BigDecimal boundedLinehaul,
        FuelSurchargeResult fuelSurcharge, List<RatingLine> lines, BigDecimal subtotal,
        String currency, int currencyScale, String taxAvailability, String roundingPolicyCode,
        int roundingPolicyVersion, Instant calculatedAt, String correlationId, String inputHash, String resultHash) {
    public RatingPreview { lines = List.copyOf(lines); }
}
