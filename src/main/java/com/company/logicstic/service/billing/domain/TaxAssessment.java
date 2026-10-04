package com.company.logicstic.service.billing.domain;

import com.company.logicstic.dto.invoice.TaxAssessmentRequest;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaxAssessment(TaxAssessmentRequest assessment, UUID capturedBy,
        OffsetDateTime capturedAt, String inputHash) { }
