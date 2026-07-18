package com.company.logicstic.dto;

public record TenantRegistryRecord(
        String tenantId,
        String dbUrl,
        String dbUsername,
        String encryptedDbPassword,
        String status) {
}
