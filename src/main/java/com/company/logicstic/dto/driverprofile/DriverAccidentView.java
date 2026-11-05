package com.company.logicstic.dto.driverprofile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Accident profile projection omits signatures, driver statements, and police-officer personal details. */
public record DriverAccidentView(
        UUID id,
        UUID driverId,
        UUID truckId,
        UUID tripId,
        String accidentType,
        String severity,
        OffsetDateTime accidentDateTime,
        Double latitude,
        Double longitude,
        String address,
        String description,
        String weatherConditions,
        String roadConditions,
        boolean anyInjuries,
        Integer numberOfInjuries,
        String injuryDescription,
        boolean vehicleDamaged,
        String vehicleDamageDescription,
        BigDecimal estimatedDamageCost,
        boolean vehicleDrivable,
        boolean policeReportFiled,
        String policeReportNumber,
        boolean insuranceNotified,
        OffsetDateTime insuranceNotifiedAt,
        String insuranceClaimNumber,
        String status,
        List<UUID> documentIds
) {}
