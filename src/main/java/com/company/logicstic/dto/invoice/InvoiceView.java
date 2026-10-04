package com.company.logicstic.dto.invoice;

import com.company.logicstic.entity.Invoice;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceView(
        UUID id,
        Long number,
        String type,
        String status,
        String taxBehavior,
        String notes,
        OffsetDateTime dueDate,
        UUID loadId,
        UUID customerId,
        String customerName,
        @Deprecated(forRemoval = false) UUID employeeId,
        @Deprecated(forRemoval = false) String employeeName,
        BigDecimal subtotalAmount,
        String subtotalCurrency,
        BigDecimal taxTotalAmount,
        String taxTotalCurrency,
        BigDecimal totalAmount,
        String totalCurrency,
        OffsetDateTime sentAt,
        String sentToEmail,
        @Deprecated(forRemoval = false) OffsetDateTime periodStart,
        @Deprecated(forRemoval = false) OffsetDateTime periodEnd,
        @Deprecated(forRemoval = false) Double totalDistanceDriven,
        String invoicePurpose, Integer economicSign, UUID ratingSnapshotId,
        UUID parentInvoiceId, UUID billingChainId
) {
    public static InvoiceView from(Invoice i) {
        String empName = i.getEmployee() != null
                ? i.getEmployee().getFirstName() + " " + i.getEmployee().getLastName() : null;
        return new InvoiceView(
                i.getId(), i.getNumber(), i.getType(), i.getStatus(), i.getTaxBehavior(),
                i.getNotes(), i.getDueDate(),
                i.getLoad() != null ? i.getLoad().getId() : null,
                i.getCustomer() != null ? i.getCustomer().getId() : null,
                i.getCustomer() != null ? i.getCustomer().getName() : null,
                i.getEmployee() != null ? i.getEmployee().getId() : null, empName,
                i.getSubtotalAmount(), i.getSubtotalCurrency(),
                i.getTaxTotalAmount(), i.getTaxTotalCurrency(),
                i.getTotalAmount(), i.getTotalCurrency(),
                i.getSentAt(), i.getSentToEmail(),
                i.getPeriodStart(), i.getPeriodEnd(), i.getTotalDistanceDriven(),
                i.getInvoicePurpose(),i.getEconomicSign(),i.getRatingSnapshotId(),i.getParentInvoiceId(),i.getBillingChainId()
        );
    }
}
