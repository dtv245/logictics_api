package com.company.logicstic.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotBlank String type,
        @NotBlank String status,
        String taxBehavior,
        String notes,
        OffsetDateTime dueDate,
        UUID loadId,
        UUID customerId,
        @Deprecated(forRemoval = false) UUID employeeId,
        @NotNull BigDecimal subtotalAmount,
        @NotBlank String subtotalCurrency,
        @NotNull BigDecimal taxTotalAmount,
        @NotBlank String taxTotalCurrency,
        @NotNull BigDecimal totalAmount,
        @NotBlank String totalCurrency,
        @Deprecated(forRemoval = false) OffsetDateTime periodStart,
        @Deprecated(forRemoval = false) OffsetDateTime periodEnd,
        @Deprecated(forRemoval = false) Double totalDistanceDriven
) {
    public boolean hasLegacyPayrollFields() {
        return employeeId != null || periodStart != null || periodEnd != null || totalDistanceDriven != null;
    }
}
