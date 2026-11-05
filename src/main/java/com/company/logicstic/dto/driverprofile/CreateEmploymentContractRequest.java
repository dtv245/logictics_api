package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEmploymentContractRequest(
        @NotBlank @Size(max = 100) String contractNumber,
        @NotBlank @Pattern(regexp = "PROBATION|FIXED_TERM|INDEFINITE") String contractType,
        @NotNull LocalDate signedDate,
        @NotNull LocalDate effectiveDate,
        LocalDate expiryDate,
        UUID documentId,
        LocalDate terminationDate,
        @Size(max = 1000) String terminationReason
) {
}
