package com.company.logicstic.tenant.dto;

public record TenantProvisionResult(
    String tenantId, String databaseName, String status, String message) {}
