package com.company.logicstic.dto.terminal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTerminalRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @NotBlank(message = "Code is required")
        @Size(max = 5, message = "Code must be at most 5 characters")
        String code,

        @NotBlank(message = "Country code is required")
        @Size(max = 2, message = "Country code must be at most 2 characters")
        String countryCode,

        @NotBlank(message = "Type is required")
        String type,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        @NotBlank(message = "City is required")
        String addressCity,

        @NotBlank(message = "Country is required")
        String addressCountry,

        @NotBlank(message = "Address line 1 is required")
        String addressLine1,

        String addressLine2,

        @NotBlank(message = "State is required")
        String addressState,

        @NotBlank(message = "ZIP code is required")
        String addressZipCode
) {}
