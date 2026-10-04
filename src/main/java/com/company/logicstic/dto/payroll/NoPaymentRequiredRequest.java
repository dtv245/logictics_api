package com.company.logicstic.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoPaymentRequiredRequest(
        @NotBlank @Size(max = 40) String reasonCode,
        @NotBlank @Size(max = 1000) String reason) {
}
