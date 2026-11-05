package com.company.logicstic.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateCustomerRequest(
        @NotBlank String name,
        @Email String email,
        String phone,
        @NotBlank String status,
        String notes,
        String taxId,
        @NotNull Boolean isVatExempt,
        String addressLine1,
        String addressLine2,
        String addressCity,
        String addressState,
        String addressZipCode,
        String addressCountry,
        @PositiveOrZero(message = "Payment terms days must not be negative") Integer paymentTermsDays,
        @Pattern(regexp = "^[A-Z]{3}$", message = "Default currency must be a 3-letter ISO 4217 code")
        String defaultCurrency
) {}
