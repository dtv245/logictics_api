package com.company.logicstic.dto.payroll;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManualPayrollBankReconciliationRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotNull UUID caseEventId,
        @NotBlank @Size(max = 100) String bankSource,
        @NotBlank @Size(max = 200) String transactionReference,
        @NotNull @DecimalMin(value = "0.0000") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotBlank @Pattern(regexp = "SUCCEEDED|FAILED") String outcome,
        @NotBlank @Size(max = 200) String counterpartyReference,
        @NotBlank @Size(max = 500) String evidenceReference,
        @NotBlank @Size(max = 500) String reason,
        @NotNull OffsetDateTime occurredAt) {}
