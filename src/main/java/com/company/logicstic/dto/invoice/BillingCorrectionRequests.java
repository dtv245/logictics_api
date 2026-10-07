package com.company.logicstic.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class BillingCorrectionRequests {
    private BillingCorrectionRequests() { }
    public record Supplemental(@NotNull @Valid GenerateInvoiceRequest generation,
            @NotEmpty List<@NotNull UUID> chargeIds, @NotBlank @Size(max=80) String reasonCode,
            @NotBlank @Size(max=1000) String reason) { }
    public record Credit(@NotBlank @Size(max=120) String idempotencyKey,
            @NotEmpty List<@NotNull @Valid CreditLine> lines,
            @NotNull @Valid GenerateInvoiceRequest.TaxDecision taxDecision,
            @NotBlank @Size(max=80) String reasonCode, @NotBlank @Size(max=1000) String reason) { }
    public record CreditLine(@NotNull UUID originalLineId, @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal amount,
            @NotNull @DecimalMin("0") BigDecimal taxAmount, @DecimalMin(value="0",inclusive=false) BigDecimal quantity) { }
    public record Rebill(@NotNull @Valid GenerateInvoiceRequest generation,
            @NotEmpty List<@NotNull UUID> creditEvidenceIds, @NotBlank @Size(max=80) String reasonCode,
            @NotBlank @Size(max=1000) String reason) { }
}
