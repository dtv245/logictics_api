package com.company.logicstic.dto.payment;

import com.company.logicstic.entity.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentView(
        UUID id,
        String status,
        UUID invoiceId,
        Long invoiceNumber,
        BigDecimal amountAmount,
        String amountCurrency,
        String description,
        String referenceNumber,
        OffsetDateTime recordedAt,
        String billingAddressLine1,
        String billingAddressLine2,
        String billingAddressCity,
        String billingAddressState,
        String billingAddressZipCode,
        String billingAddressCountry
) {
    public static PaymentView from(Payment p) {
        return new PaymentView(
                p.getId(), p.getStatus(),
                p.getInvoice() != null ? p.getInvoice().getId() : null,
                p.getInvoice() != null ? p.getInvoice().getNumber() : null,
                p.getAmountAmount(), p.getAmountCurrency(),
                p.getDescription(), p.getReferenceNumber(), p.getRecordedAt(),
                p.getBillingAddressLine1(), p.getBillingAddressLine2(), p.getBillingAddressCity(),
                p.getBillingAddressState(), p.getBillingAddressZipCode(), p.getBillingAddressCountry()
        );
    }
}
