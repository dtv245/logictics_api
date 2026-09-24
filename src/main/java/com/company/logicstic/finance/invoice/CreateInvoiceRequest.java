package com.company.logicstic.finance.invoice;

import com.company.logicstic.shared.validation.CurrencyCodes;
import com.company.logicstic.shared.validation.IsoCurrency;
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
    UUID employeeId,
    @NotNull BigDecimal subtotalAmount,
    @NotBlank @IsoCurrency String subtotalCurrency,
    @NotNull BigDecimal taxTotalAmount,
    @NotBlank @IsoCurrency String taxTotalCurrency,
    @NotNull BigDecimal totalAmount,
    @NotBlank @IsoCurrency String totalCurrency,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    Double totalDistanceDriven) {
  public CreateInvoiceRequest {
    subtotalCurrency = CurrencyCodes.normalize(subtotalCurrency);
    taxTotalCurrency = CurrencyCodes.normalize(taxTotalCurrency);
    totalCurrency = CurrencyCodes.normalize(totalCurrency);
  }
}
