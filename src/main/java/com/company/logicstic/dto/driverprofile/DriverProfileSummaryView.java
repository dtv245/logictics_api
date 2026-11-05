package com.company.logicstic.dto.driverprofile;

import java.util.List;
import java.util.UUID;

public record DriverProfileSummaryView(
        UUID driverId,
        DriverProfileCounts counts,
        DriverComplianceStatusView license,
        DriverComplianceStatusView healthCheck,
        DriverComplianceStatusView contract,
        DriverComplianceStatusView healthInsurance,
        List<DriverExpiringItemView> upcomingExpirations,
        boolean upcomingExpirationsTruncated
) {
}
