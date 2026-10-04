package com.company.logicstic.dto.accessorial;

import java.math.BigDecimal;
import java.util.UUID;

public record DetentionCalculationResult(
        UUID stopId,
        long dwellMinutes,
        long excessMinutes,
        BigDecimal billableUnits,
        BigDecimal customerAmount,
        BigDecimal driverPayAmount,
        String currency,
        BigDecimal dwellSeconds,
        BigDecimal excessSeconds
) {}
