package com.company.logicstic.dto.driverprofile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Clear insurance identifiers are only returned by the ADMIN-sensitive endpoint. */
public record DriverInsuranceSensitiveView(
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
