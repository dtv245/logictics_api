package com.company.logicstic.dto.invoice;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GenerateInvoiceRequest(String idempotencyKey, UUID snapshotId,
        TaxDecision taxDecision, List<LineTax> lineTaxes) {
    /** Accounting owns this explicit decision; capture actor/time come from the command boundary. */
    public record TaxDecision(String requirement, String reasonCode, String reason,
            String sourceReference, UUID assessmentId) { }
    public record LineTax(String componentType, UUID sourceId, BigDecimal taxAmount) { }
}
