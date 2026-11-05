package com.company.logicstic.dto.driverprofile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverOperationalStatusView(
        UUID driverId,
        String availability,
        UUID defaultVehicleId,
        UUID currentTripId,
        BigDecimal lastKnownLatitude,
        BigDecimal lastKnownLongitude,
        OffsetDateTime lastKnownAt,
        BigDecimal leaveBalanceDays,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt
) {
}
