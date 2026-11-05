package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDriverBankAccountRequest(
        @NotBlank @Size(max = 128) String bankName,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9 -]{8,34}|\\*{4}[A-Za-z0-9]{4}") String accountNumber,
        @NotBlank @Size(max = 255) String accountHolderName,
        @NotNull Boolean primary,
        @NotNull Boolean active
) {
}
