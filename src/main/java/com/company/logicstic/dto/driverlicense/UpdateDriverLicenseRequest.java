package com.company.logicstic.dto.driverlicense;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateDriverLicenseRequest(
        @Size(max = 64) String licenseNumber,
        @NotBlank @Size(max = 64) String licenseClass,
        @Size(max = 1000) String endorsements,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String issuingCountry,
        @Size(max = 64) String issuingRegion,
        @NotNull @PastOrPresent OffsetDateTime issuedDate,
        @NotNull OffsetDateTime expiryDate,
        @Size(max = 255) String issuedPlace,
        @Min(0) @Max(12) Integer remainingPoints,
        OffsetDateTime medicalCertificateExpiryDate,
        UUID documentId,
        UUID frontImageDocumentId,
        UUID backImageDocumentId,
        @NotNull Boolean active
) {
}
