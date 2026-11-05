package com.company.logicstic.dto.driverlicense;

import com.company.logicstic.dto.driverprofile.ExpiryStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverLicenseView(
        UUID id,
        UUID driverId,
        String licenseNumber,
        String licenseClass,
        String endorsements,
        String issuingCountry,
        String issuingRegion,
        OffsetDateTime issuedDate,
        OffsetDateTime expiryDate,
        OffsetDateTime medicalCertificateExpiryDate,
        String issuedPlace,
        Integer remainingPoints,
        UUID documentId,
        UUID frontImageDocumentId,
        UUID backImageDocumentId,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt,
        ExpiryStatus expiryStatus,
        ExpiryStatus medicalCertificateExpiryStatus
) {
}
