package com.company.logicstic.dto.driverprofile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Returned only by the ADMIN-only sensitive profile endpoint. */
public record DriverProfileSensitiveView(
        UUID id,
        UUID driverId,
        LocalDate dateOfBirth,
        String gender,
        String nationality,
        String maritalStatus,
        String educationLevel,
        String nationalIdNumber,
        LocalDate nationalIdIssuedDate,
        String nationalIdIssuedPlace,
        String personalTaxCode,
        String avatarUrl,
        String permanentAddress,
        String temporaryAddress,
        String hometown,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt
) {
}
