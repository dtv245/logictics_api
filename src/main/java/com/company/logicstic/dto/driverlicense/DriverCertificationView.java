package com.company.logicstic.dto.driverlicense;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverCertificationView(
        UUID id,
        UUID driverId,
        String type,
        String name,
        LocalDate issuedDate,
        LocalDate expiryDate,
        String issuer,
        UUID documentId,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt,
        ExpiryStatus expiryStatus
) {
}
