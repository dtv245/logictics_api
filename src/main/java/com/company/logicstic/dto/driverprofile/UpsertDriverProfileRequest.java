package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PUT is an upsert because each driver has at most one profile resource. */
public record UpsertDriverProfileRequest(
        @PastOrPresent LocalDate dateOfBirth,
        @Pattern(regexp = "MALE|FEMALE|OTHER|UNSPECIFIED") String gender,
        @Size(max = 80) String nationality,
        @Pattern(regexp = "SINGLE|MARRIED|DIVORCED|WIDOWED|OTHER") String maritalStatus,
        @Size(max = 80) String educationLevel,
        @Pattern(regexp = "[0-9]{9}|[0-9]{12}|\\*{4}[0-9]{4}") String nationalIdNumber,
        @PastOrPresent LocalDate nationalIdIssuedDate,
        @Size(max = 255) String nationalIdIssuedPlace,
        @Pattern(regexp = "[0-9]{10}|[0-9]{13}|\\*{4}[0-9]{4}") String personalTaxCode,
        @Size(max = 2048) String avatarUrl,
        @Size(max = 1000) String permanentAddress,
        @Size(max = 1000) String temporaryAddress,
        @Size(max = 255) String hometown
) {
}
