package com.company.logicstic.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Accounting-authenticated capture; this is evidence, not a jurisdiction tax engine. */
public record TaxAssessmentRequest(
        UUID assessmentId,
        @NotNull UUID loadId,
        @NotNull UUID customerId,
        @NotBlank String sourceType,
        @NotBlank String sourceReference,
        @NotBlank String jurisdiction,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal taxableBasis,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") BigDecimal taxAmount,
        @NotBlank String currency,
        String policyVersion,
        OffsetDateTime assessedAt,
        String assessedBy
) { }
