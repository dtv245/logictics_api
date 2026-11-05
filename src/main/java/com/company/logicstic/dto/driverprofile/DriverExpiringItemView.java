package com.company.logicstic.dto.driverprofile;

import java.time.LocalDate;
import java.util.UUID;

public record DriverExpiringItemView(
        UUID itemId,
        UUID driverId,
        String driverName,
        String type,
        String itemName,
        LocalDate expiryDate,
        long daysUntilExpiry,
        ExpiryStatus expiryStatus
) {
}
