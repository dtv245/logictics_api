package com.company.logicstic.dto.accessorial;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateAccessorialChargeRequest(
        UUID tripId,
        UUID tripStopId,
        @NotBlank String type,
        @PositiveOrZero BigDecimal quantity,
        String unit,
        @PositiveOrZero BigDecimal rate,
        @PositiveOrZero BigDecimal freeQuantity,
        @NotNull @PositiveOrZero BigDecimal customerAmount,
        @PositiveOrZero BigDecimal companyCostAmount,
        @PositiveOrZero BigDecimal driverPayAmount,
        @NotBlank String currency,
        OffsetDateTime occurredAt,
        UUID documentId,
        String note
) {}
