package com.company.logicstic.modules.terminal.dto.request;

import com.company.logicstic.modules.terminal.enums.TerminalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a terminal.
 *
 * <p>Validation here is structural only — present, length, format. Uniqueness of the UN/LOCODE and
 * any cross-entity rule belong to the service, which owns the database and can answer them.
 *
 * <p>Field lengths mirror the {@code terminals} table: name 200, code 5, country_code 2, notes
 * 2000.
 */
public record CreateTerminalRequest(
    @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,
    @NotBlank(message = "UN/LOCODE is required")
        @Pattern(
            regexp = "^[A-Za-z]{5}$",
            message = "UN/LOCODE must be exactly 5 letters, for example USNYC")
        String code,
    @NotBlank(message = "Country code is required")
        @Pattern(
            regexp = "^[A-Za-z]{2}$",
            message = "Country code must be an ISO 3166-1 alpha-2 code, for example US")
        String countryCode,
    @NotNull(message = "Terminal type is required") TerminalType type,
    @Size(max = 2000, message = "Notes must be at most 2000 characters") String notes,
    @NotBlank(message = "Address line 1 is required") String addressLine1,
    String addressLine2,
    @NotBlank(message = "City is required") String addressCity,
    @NotBlank(message = "State is required") String addressState,
    @NotBlank(message = "ZIP code is required") String addressZipCode,
    @NotBlank(message = "Country is required") String addressCountry) {}
