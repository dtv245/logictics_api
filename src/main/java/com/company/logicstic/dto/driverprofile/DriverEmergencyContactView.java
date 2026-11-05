package com.company.logicstic.dto.driverprofile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverEmergencyContactView(
        UUID id,
        UUID driverId,
        String fullName,
        String relationship,
        String phoneNumber,
        String address,
        boolean primary,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt
) {
}
