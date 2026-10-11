package com.company.logicstic.service;

import javax.sql.DataSource;

import com.company.logicstic.config.TenancyProperties;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.dto.TenantRegistryRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.company.logicstic.config.DatabaseUrlUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantDataSourceService {

    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantRegistryService tenantRegistryService;
    private final TenantCredentialCipher credentialCipher;
    private final TenancyProperties properties;

    public DataSource ensureTenantDataSource(String tenantId) {
        // A cached connection pool does not authorize an inactive tenant.
        tenantRegistryService.findActiveTenant(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant is not active or not registered"));
        return tenantRoutingDataSource.getRegisteredDataSource(tenantId)
                .orElseGet(() -> createAndRegisterTenantDataSource(tenantId));
    }

    private synchronized DataSource createAndRegisterTenantDataSource(String tenantId) {
        return tenantRoutingDataSource.getRegisteredDataSource(tenantId)
                .orElseGet(() -> {
                    enforcePoolLimit();
                    TenantRegistryRecord tenant = tenantRegistryService.findActiveTenant(tenantId)
                            .orElseThrow(() -> new IllegalArgumentException("Tenant is not active or not registered: "
                                    + tenantId));
                    HikariDataSource dataSource = buildDataSource(tenant);
                    tenantRoutingDataSource.addTenantDataSource(tenantId, dataSource);
                    return dataSource;
                });
    }

    public DataSource buildTemporaryDataSource(TenantRegistryRecord tenant) {
        return buildDataSource(tenant);
    }

    private HikariDataSource buildDataSource(TenantRegistryRecord tenant) {
        if (!StringUtils.hasText(tenant.dbUrl())) {
            throw new IllegalArgumentException(
                "Tenant database URL is required but missing in registry for tenant: " + tenant.tenantId());
        }

        String decryptedPassword = credentialCipher.decrypt(tenant.encryptedDbPassword());
        DatabaseUrlUtils.NormalizedJdbcUrl normalized = DatabaseUrlUtils.normalize(
                tenant.dbUrl(), tenant.dbUsername(), decryptedPassword);

        TenancyProperties.Pool pool = properties.getPool();
        HikariConfig config = new HikariConfig();
        config.setPoolName("tenant-" + tenant.tenantId());
        config.setJdbcUrl(normalized.jdbcUrl());
        config.setUsername(normalized.username());
        config.setPassword(normalized.password());
        config.setMaximumPoolSize(pool.getMaximumPoolSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setConnectionTimeout(pool.getConnectionTimeoutMs());
        config.setIdleTimeout(pool.getIdleTimeoutMs());
        return new HikariDataSource(config);
    }

    private void enforcePoolLimit() {
        int maxTenantPools = properties.getPool().getMaxTenantPools();
        if (tenantRoutingDataSource.getRegisteredTenantCount() >= maxTenantPools) {
            throw new IllegalStateException("Maximum tenant connection pool count reached: " + maxTenantPools);
        }
    }
}
