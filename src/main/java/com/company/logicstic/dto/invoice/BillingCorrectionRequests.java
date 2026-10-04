package com.company.logicstic.dto.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class BillingCorrectionRequests {
    private BillingCorrectionRequests() { }
    public record Supplemental(GenerateInvoiceRequest generation,List<UUID> chargeIds,String reasonCode,String reason) { }
    public record Credit(String idempotencyKey,List<CreditLine> lines,GenerateInvoiceRequest.TaxDecision taxDecision,String reasonCode,String reason) { }
    public record CreditLine(UUID originalLineId,BigDecimal amount,BigDecimal taxAmount,BigDecimal quantity) { }
    public record Rebill(GenerateInvoiceRequest generation,List<UUID> creditEvidenceIds,String reasonCode,String reason) { }
}
