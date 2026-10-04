package com.company.logicstic.service.billing.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Command outcome retains exact issued/generated basis, independently of current status. */
public record BillingInvoice(UUID invoiceId, UUID loadId, UUID customerId, String currency,
        String purpose, int economicSign, String status, UUID snapshotId, UUID parentInvoiceId,
        UUID billingChainId, BigDecimal subtotal, BigDecimal tax, BigDecimal total,
        List<Line> lines, UUID commandId, UUID actor, OffsetDateTime capturedAt) {
    public BillingInvoice { lines=List.copyOf(lines); }
    public record Line(UUID lineId,String componentType,UUID sourceId,String description,
            BigDecimal amount,BigDecimal taxAmount,UUID creditedLineId,BigDecimal creditedQuantity) { }
}
