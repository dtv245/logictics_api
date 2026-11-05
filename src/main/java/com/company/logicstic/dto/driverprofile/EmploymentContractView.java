package com.company.logicstic.dto.driverprofile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EmploymentContractView(
        UUID id,
        UUID driverId,
        String contractNumber,
        String contractType,
        LocalDate signedDate,
        LocalDate effectiveDate,
        LocalDate expiryDate,
        UUID documentId,
        LocalDate terminationDate,
        String terminationReason,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt,
        ExpiryStatus expiryStatus
) {
}
