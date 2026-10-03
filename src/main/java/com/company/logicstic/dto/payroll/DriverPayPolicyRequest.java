package com.company.logicstic.dto.payroll;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DriverPayPolicyRequest(
        @NotBlank @Size(max = 80) String policyCode,
        @NotBlank @Size(max = 200) String name,
        UUID driverId,
        @NotBlank @Pattern(regexp = "PER_MILE|PER_LOAD|PERCENT_REVENUE|HOURLY|DAILY|FLAT_RATE") String payMethod,
        @PositiveOrZero BigDecimal perMileRate,
        @PositiveOrZero BigDecimal perLoadRate,
        @PositiveOrZero BigDecimal hourlyRate,
        @PositiveOrZero BigDecimal dailyRate,
        @PositiveOrZero BigDecimal flatRate,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal revenuePercentage,
        String mileageBasis,
        String revenueBasis,
        @PositiveOrZero BigDecimal detentionRate,
        @PositiveOrZero Integer detentionFreeMinutes,
        @Positive Integer detentionBlockMinutes,
        @PositiveOrZero BigDecimal layoverRate,
        @PositiveOrZero BigDecimal stopPayRate,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
