package com.company.logicstic.dto.trip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignDriverRequest(
        @NotNull UUID driverId,
        String assignmentType,
        OffsetDateTime effectiveFrom,
        BigDecimal plannedMiles
) {}
