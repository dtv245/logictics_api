package com.company.logicstic.dto.trip;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TripStopView(
        UUID id,
        UUID tripId,
        UUID loadId,
        Integer order,
        String type,
        String status,
        OffsetDateTime appointmentStart,
        OffsetDateTime appointmentEnd,
        OffsetDateTime arrivedAt,
        OffsetDateTime serviceStartedAt,
        OffsetDateTime serviceCompletedAt,
        OffsetDateTime departedAt,
        Long dwellMinutes,
        String addressCity,
        String addressState,
        Double latitude,
        Double longitude
) {}
