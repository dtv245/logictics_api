package com.company.logicstic.dto.driverprofile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverViolationView(
        UUID id,
        UUID driverId,
        OffsetDateTime violationDate,
        String violationType,
        String description,
        BigDecimal fineAmount,
        String fineCurrency,
        UUID tripId,
        UUID documentId,
        String resolutionStatus,
        boolean active
) {}
