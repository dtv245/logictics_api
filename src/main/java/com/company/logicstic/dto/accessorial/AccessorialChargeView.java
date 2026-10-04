package com.company.logicstic.dto.accessorial;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccessorialChargeView(
        UUID id,
        UUID loadId,
        UUID tripId,
        UUID tripStopId,
        String type,
        String status,
        BigDecimal quantity,
        String unit,
        BigDecimal rate,
        BigDecimal freeQuantity,
        BigDecimal customerAmount,
        BigDecimal companyCostAmount,
        BigDecimal driverPayAmount,
        String currency,
        OffsetDateTime occurredAt,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        UUID documentId,
        String note,
        Long version
) {}
