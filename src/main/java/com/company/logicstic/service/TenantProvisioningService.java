package com.company.logicstic.service;

import java.util.concurrent.CompletableFuture;

import com.company.logicstic.config.TenancyProperties;
import com.company.logicstic.dto.TenantProvisionRequest;
import com.company.logicstic.dto.TenantProvisionResult;
import com.company.logicstic.dto.TenantRegistryRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantProvisioningService {

    private final TenancyProperties properties;
    private final TenantRegistryService tenantRegistryService;
    private final TenantMigrationService tenantMigrationService;
    private final TenantCredentialCipher credentialCipher;

    @Async
    public CompletableFuture<TenantProvisionResult> provisionTenant(TenantProvisionRequest request) {
        createDatabase(request.databaseName());
        TenantRegistryRecord tenant = new TenantRegistryRecord(
                request.tenantId(),
                request.dbUrl(),
                request.dbUsername(),
                credentialCipher.encrypt(request.dbPassword()),
                "ACTIVE");

        tenantMigrationService.migrateTenant(tenant);
        tenantRegistryService.registerActiveTenant(
                request.tenantId(),
                request.dbUrl(),
                request.dbUsername(),
                request.dbPassword());

        return CompletableFuture.completedFuture(new TenantProvisionResult(
                request.tenantId(),
                request.databaseName(),
                "ACTIVE",
                "Tenant database provisioned and registered"));
    }

    private void createDatabase(String databaseName) {
        TenancyProperties.Provisioning provisioning = properties.getProvisioning();
        if (!StringUtils.hasText(provisioning.getAdminUrl())) {
            throw new IllegalStateException("app.tenancy.provisioning.admin-url is required for tenant provisioning");
        }

        try (HikariDataSource dataSource = buildAdminDataSource(provisioning)) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String sql = "CREATE DATABASE " + quoteIdentifier(databaseName);
            if (StringUtils.hasText(provisioning.getTemplateDatabase())) {
                sql += " TEMPLATE " + quoteIdentifier(provisioning.getTemplateDatabase());
            }
            jdbcTemplate.execute(sql);
        }
    }

    private HikariDataSource buildAdminDataSource(TenancyProperties.Provisioning provisioning) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("tenant-provisioning-admin");
        config.setJdbcUrl(provisioning.getAdminUrl());
        config.setUsername(provisioning.getAdminUsername());
        config.setPassword(provisioning.getAdminPassword());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }

    private String quoteIdentifier(String identifier) {
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Database identifier contains unsupported characters");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
