package com.company.logicstic.tenant.dto;

public record TenantRegistryRecord(
    String tenantId, String dbUrl, String dbUsername, String encryptedDbPassword, String status) {}
