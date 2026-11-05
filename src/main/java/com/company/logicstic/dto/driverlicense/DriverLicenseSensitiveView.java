package com.company.logicstic.dto.driverlicense;

import java.util.UUID;

/** ADMIN-only response that exposes the unmasked driving licence number. */
public record DriverLicenseSensitiveView(UUID id, UUID driverId, String licenseNumber) {
}
