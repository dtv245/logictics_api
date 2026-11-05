package com.company.logicstic.dto.driverprofile;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Clear bank account number is only returned by the ADMIN-sensitive endpoint. */
public record DriverBankAccountSensitiveView(
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
