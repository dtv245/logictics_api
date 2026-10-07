package com.company.logicstic.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GenerateInvoiceRequest(@NotBlank @Size(max=120) String idempotencyKey, @NotNull UUID snapshotId,
        @NotNull @Valid TaxDecision taxDecision, @NotNull List<@NotNull @Valid LineTax> lineTaxes) {
    /** Accounting owns this explicit decision; capture actor/time come from the command boundary. */
    public record TaxDecision(@NotBlank @Pattern(regexp="REQUIRED|NOT_REQUIRED") String requirement,
            @NotBlank @Size(max=80) String reasonCode, @NotBlank @Size(max=1000) String reason,
            @NotBlank @Size(max=1000) String sourceReference, UUID assessmentId) { }
    public record LineTax(@NotBlank String componentType, @NotNull UUID sourceId,
            @NotNull @DecimalMin("0") BigDecimal taxAmount) { }
}
