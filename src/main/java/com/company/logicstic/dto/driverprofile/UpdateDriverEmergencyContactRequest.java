package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDriverEmergencyContactRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 64) String relationship,
        @NotBlank @Pattern(regexp = "\\+?[0-9().\\-\\s]{8,24}") String phoneNumber,
        @Size(max = 1000) String address,
        @NotNull Boolean primary,
        @NotNull Boolean active
) {
}
