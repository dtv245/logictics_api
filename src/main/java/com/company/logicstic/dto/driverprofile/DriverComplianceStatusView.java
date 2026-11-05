package com.company.logicstic.dto.driverprofile;

public record DriverComplianceStatusView(
        boolean hasActiveRecord,
        ExpiryStatus expiryStatus
) {
}
