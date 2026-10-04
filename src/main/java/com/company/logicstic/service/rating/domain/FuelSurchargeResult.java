package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;

public record FuelSurchargeResult(IndexBasedFscPolicy policy, FuelIndexObservation index,
        ResolvedRatingMileage mileage, BigDecimal rawPerMile, BigDecimal perMile,
        BigDecimal unroundedTotal, BigDecimal total, String currency,
        String roundingPolicyCode, int roundingPolicyVersion) { }
