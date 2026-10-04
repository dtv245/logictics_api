package com.company.logicstic.dto.invoice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Accounting-authenticated capture; this is evidence, not a jurisdiction tax engine. */
public record TaxAssessmentRequest(UUID assessmentId, UUID loadId, UUID customerId,
        String sourceType, String sourceReference, String jurisdiction, BigDecimal taxableBasis,
        BigDecimal taxAmount, String currency, String policyVersion, OffsetDateTime assessedAt,
        String assessedBy) { }
