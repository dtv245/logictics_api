package com.company.logicstic.dto.accessorial;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DetentionCalculationRequest(
        @PositiveOrZero int freeMinutes,
        @PositiveOrZero int blockMinutes,
        @NotNull @PositiveOrZero BigDecimal hourlyRate,
        @PositiveOrZero BigDecimal driverHourlyRate,
        @NotBlank String currency
) {}
