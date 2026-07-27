package com.company.logicstic.modules.finance.dto.response;

import com.company.logicstic.modules.finance.entity.Invoice;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceResponse(
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
    UUID employeeId,
    String employeeName,
    BigDecimal subtotalAmount,
    String subtotalCurrency,
    BigDecimal taxTotalAmount,
    String taxTotalCurrency,
    BigDecimal totalAmount,
    String totalCurrency,
    OffsetDateTime sentAt,
    String sentToEmail,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    Double totalDistanceDriven) {
  public static InvoiceResponse from(Invoice i) {
    String empName =
        i.getEmployee() != null
            ? i.getEmployee().getFirstName() + " " + i.getEmployee().getLastName()
            : null;
    return new InvoiceResponse(
        i.getId(),
        i.getNumber(),
        i.getType(),
        i.getStatus(),
        i.getTaxBehavior(),
        i.getNotes(),
        i.getDueDate(),
        i.getLoad() != null ? i.getLoad().getId() : null,
        i.getCustomer() != null ? i.getCustomer().getId() : null,
        i.getCustomer() != null ? i.getCustomer().getName() : null,
        i.getEmployee() != null ? i.getEmployee().getId() : null,
        empName,
        i.getSubtotalAmount(),
        i.getSubtotalCurrency(),
        i.getTaxTotalAmount(),
        i.getTaxTotalCurrency(),
        i.getTotalAmount(),
        i.getTotalCurrency(),
        i.getSentAt(),
        i.getSentToEmail(),
        i.getPeriodStart(),
        i.getPeriodEnd(),
        i.getTotalDistanceDriven());
  }
}
