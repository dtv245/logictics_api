package com.company.logicstic.dto.invoice;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable invoice line projection; rated provenance columns are null for legacy invoices. */
public record InvoiceLineView(
        UUID id,
        UUID invoiceId,
        String description,
        String type,
        Integer quantity,
        Integer order,
        String notes,
        BigDecimal taxRatePercent,
        BigDecimal taxAmount,
        String taxCode,
        BigDecimal amountAmount,
        String amountCurrency,
        UUID ratingSourceId,
        UUID creditedLineId,
        BigDecimal creditedQuantity
) { }
