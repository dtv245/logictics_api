package com.company.logicstic.service.rating.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable published version. Null dimensions are wildcards, not fallback weights. */
public record RateRule(UUID ruleId, int version, int priority, UUID customerId,
                       UUID contractId, Integer contractVersion, String lane, String equipment,
                       String service, String tier, String currency, LocalDate effectiveFrom,
                       LocalDate effectiveTo, RatingMethod method, BigDecimal baseRate,
                       RatingMileageBasis linehaulMileageBasis, BigDecimal minimumCharge,
                       BigDecimal maximumCharge, IndexBasedFscPolicy fsc,
                       String roundingPolicyCode, int roundingPolicyVersion,
                       UUID createdBy, OffsetDateTime createdAt) { }
