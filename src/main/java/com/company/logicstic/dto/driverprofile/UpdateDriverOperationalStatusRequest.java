package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateDriverOperationalStatusRequest(
        @NotBlank @Pattern(regexp = "(?i)AVAILABLE|ON_TRIP|OFF_DUTY|ON_LEAVE|SUSPENDED") String availability,
        UUID defaultVehicleId,
        @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 3, fraction = 7) BigDecimal lastKnownLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7) BigDecimal lastKnownLongitude,
        OffsetDateTime lastKnownAt,
        @DecimalMin("0.0") @Digits(integer = 4, fraction = 2) BigDecimal leaveBalanceDays
) {
}
