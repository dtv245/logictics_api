package com.company.logicstic.dto.driverprofile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateDriverAccidentRequest(
        @NotNull UUID truckId,
        UUID tripId,
        @NotBlank @Size(max = 100) String accidentType,
        @NotBlank @Size(max = 50) String severity,
        @NotNull OffsetDateTime accidentDateTime,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 500) String address,
        @Size(max = 4000) String description,
        @Size(max = 200) String weatherConditions,
        @Size(max = 200) String roadConditions,
        @NotNull Boolean anyInjuries,
        @Min(0) Integer numberOfInjuries,
        @Size(max = 2000) String injuryDescription,
        @NotNull Boolean vehicleDamaged,
        @Size(max = 2000) String vehicleDamageDescription,
        @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal estimatedDamageCost,
        @NotNull Boolean vehicleDrivable,
        @NotNull Boolean policeReportFiled,
        @Size(max = 100) String policeReportNumber,
        @NotNull Boolean insuranceNotified,
        OffsetDateTime insuranceNotifiedAt,
        @Size(max = 100) String insuranceClaimNumber,
        @Pattern(regexp = "REPORTED|UNDER_REVIEW|RESOLVED|CLOSED|CANCELLED") String status,
        UUID documentId
) {}
