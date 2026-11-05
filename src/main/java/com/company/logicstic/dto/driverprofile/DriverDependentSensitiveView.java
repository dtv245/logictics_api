package com.company.logicstic.dto.driverprofile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Clear dependent identifiers are only returned by ADMIN-sensitive endpoints. */
public record DriverDependentSensitiveView(
        UUID id,
        UUID driverId,
        String fullName,
        String relationship,
        LocalDate dateOfBirth,
        String nationalIdNumber,
        String taxCode,
        LocalDate deductionStartDate,
        LocalDate deductionEndDate,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt
) {
}
