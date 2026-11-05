package com.company.logicstic.dto.driverlicense;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverHealthCheckView(
        UUID id,
        UUID driverId,
        LocalDate checkedDate,
        LocalDate expiryDate,
        String facility,
        String conclusion,
        UUID documentId,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt,
        ExpiryStatus expiryStatus
) {
}
