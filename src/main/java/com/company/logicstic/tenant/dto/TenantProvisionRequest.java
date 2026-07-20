package com.company.logicstic.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantProvisionRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9_-]+")
        String tenantId,

        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_]+")
        String databaseName,

        @NotBlank
        @Size(max = 255)
        String dbUrl,

        @NotBlank
        @Size(max = 100)
        String dbUsername,

        @NotBlank
        @Size(max = 255)
        String dbPassword) {
}
