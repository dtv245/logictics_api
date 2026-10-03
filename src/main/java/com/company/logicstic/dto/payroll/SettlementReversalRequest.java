package com.company.logicstic.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettlementReversalRequest(@NotBlank @Size(max = 300) String reason) {}
