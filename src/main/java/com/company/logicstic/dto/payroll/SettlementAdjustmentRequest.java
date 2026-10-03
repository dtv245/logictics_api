package com.company.logicstic.dto.payroll;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SettlementAdjustmentRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        @NotBlank @Size(max = 300) String reason,
        @NotEmpty List<@Valid Line> lines
) {
    public record Line(
            @NotBlank @Pattern(regexp = "EARNING|DEDUCTION|REIMBURSEMENT") String lineClass,
            @NotBlank @Size(max = 50) String lineType,
            @NotBlank @Size(max = 300) String description,
            @NotNull @Positive BigDecimal amount,
            UUID loadId,
            UUID tripId
    ) {}
}
