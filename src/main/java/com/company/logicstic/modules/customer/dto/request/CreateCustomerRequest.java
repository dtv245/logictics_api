package com.company.logicstic.modules.customer.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
    String addressCountry) {}
