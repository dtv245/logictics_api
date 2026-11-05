package com.company.logicstic.dto.driverlicense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateDriverCertificationRequest(
        @NotBlank @Pattern(regexp = "TRANSPORT_OPERATIONS|SAFETY|DANGEROUS_GOODS|OTHER") String type,
        @NotBlank @Size(max = 255) String name,
        @NotNull @PastOrPresent LocalDate issuedDate,
        LocalDate expiryDate,
        @Size(max = 255) String issuer,
        UUID documentId
) {
}
