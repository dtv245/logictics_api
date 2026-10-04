package com.company.logicstic.dto.cost;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentCostView(
        UUID id,
        UUID loadId,
        UUID tripId,
        UUID truckId,
        UUID driverId,
        String category,
        String costBasis,
        String status,
        String sourceType,
        UUID sourceId,
        String allocationMethod,
        BigDecimal quantity,
        String unit,
        BigDecimal unitRate,
        BigDecimal amount,
        String currency,
        OffsetDateTime incurredAt,
        OffsetDateTime verifiedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime postedAt,
        String note,
        Long version
) {}
