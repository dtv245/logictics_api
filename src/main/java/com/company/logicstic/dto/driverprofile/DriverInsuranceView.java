package com.company.logicstic.dto.driverprofile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverInsuranceView(
        UUID id,
        UUID driverId,
        String socialInsuranceNumber,
        String healthInsuranceCardNumber,
        String registeredClinic,
        LocalDate participationStartDate,
        LocalDate participationEndDate,
        String socialInsuranceStatus,
        String healthInsuranceStatus,
        String unemploymentInsuranceStatus,
        BigDecimal insuredSalary,
        LocalDate healthInsuranceExpiryDate,
        String note,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt,
        ExpiryStatus expiryStatus
) {
}
