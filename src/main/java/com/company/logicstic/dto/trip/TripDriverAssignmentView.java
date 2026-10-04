package com.company.logicstic.dto.trip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TripDriverAssignmentView(
        UUID id,
        UUID tripId,
        UUID driverId,
        String driverName,
        String assignmentType,
        OffsetDateTime assignedAt,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        BigDecimal plannedMiles,
        BigDecimal actualMiles,
        boolean isActive
) {}
