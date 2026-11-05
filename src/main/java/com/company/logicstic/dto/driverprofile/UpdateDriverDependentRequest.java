package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateDriverDependentRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 64) String relationship,
        @PastOrPresent LocalDate dateOfBirth,
        @Pattern(regexp = "[0-9]{9}|[0-9]{12}|\\*{4}[0-9]{4}") String nationalIdNumber,
        @Pattern(regexp = "[0-9]{10}|[0-9]{13}|\\*{4}[0-9]{4}") String taxCode,
        LocalDate deductionStartDate,
        LocalDate deductionEndDate,
        @NotNull Boolean active
) {
}
