package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateDriverViolationRequest(
        @NotNull OffsetDateTime violationDate,
        @NotBlank @Size(max = 100) String violationType,
        @NotBlank @Size(max = 4000) String description,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal fineAmount,
        @Pattern(regexp = "[A-Za-z]{3}") String fineCurrency,
        UUID tripId,
        UUID documentId,
        @Pattern(regexp = "OPEN|UNDER_REVIEW|RESOLVED|DISPUTED|CANCELLED") String resolutionStatus
) {}
