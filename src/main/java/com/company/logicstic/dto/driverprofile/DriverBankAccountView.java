package com.company.logicstic.dto.driverprofile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriverBankAccountView(
        UUID id,
        UUID driverId,
        String bankName,
        String accountNumber,
        String accountHolderName,
        boolean primary,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt
) {
}
