package com.company.logicstic.dto.cost;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateShipmentCostRequest(
        UUID tripId,
        UUID truckId,
        UUID driverId,
        @NotBlank String category,
        @NotBlank String costBasis,
        String status,
        @NotBlank String sourceType,
        UUID sourceId,
        String allocationMethod,
        BigDecimal quantity,
        String unit,
        BigDecimal unitRate,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        OffsetDateTime incurredAt,
        String note
) {}
