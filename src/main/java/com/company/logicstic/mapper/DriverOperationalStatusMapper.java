package com.company.logicstic.mapper;

import com.company.logicstic.dto.driverprofile.DriverOperationalStatusView;
import com.company.logicstic.entity.DriverOperationalStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DriverOperationalStatusMapper {
    public DriverOperationalStatusView toView(DriverOperationalStatus status, UUID currentTripId) {
        return new DriverOperationalStatusView(status.getEmployee().getId(), status.getAvailability(),
                status.getDefaultVehicleId(), currentTripId, status.getLastKnownLatitude(),
                status.getLastKnownLongitude(), status.getLastKnownAt(), status.getLeaveBalanceDays(),
                status.getCreatedAt(), status.getLastModifiedAt());
    }
}
