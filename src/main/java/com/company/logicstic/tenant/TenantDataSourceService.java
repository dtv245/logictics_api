package com.company.logicstic.tenant;

import com.company.logicstic.shared.config.TenancyProperties;
import com.company.logicstic.shared.config.TenantRoutingDataSource;
import com.company.logicstic.tenant.dto.TenantRegistryRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantDataSourceService {

  private final TenantRoutingDataSource tenantRoutingDataSource;
  private final TenantRegistryService tenantRegistryService;
  private final TenantCredentialCipher credentialCipher;
  private final TenancyProperties properties;

  public DataSource ensureTenantDataSource(String tenantId) {
    return tenantRoutingDataSource
        .getRegisteredDataSource(tenantId)
        .orElseGet(() -> createAndRegisterTenantDataSource(tenantId));
  }

  private synchronized DataSource createAndRegisterTenantDataSource(String tenantId) {
    return tenantRoutingDataSource
        .getRegisteredDataSource(tenantId)
        .orElseGet(
            () -> {
              enforcePoolLimit();
              TenantRegistryRecord tenant =
                  tenantRegistryService
                      .findActiveTenant(tenantId)
                      .orElseThrow(
                          () ->
                              new IllegalArgumentException(
                                  "Tenant is not active or not registered: " + tenantId));
              HikariDataSource dataSource = buildDataSource(tenant);
              tenantRoutingDataSource.addTenantDataSource(tenantId, dataSource);
              return dataSource;
            });
  }

  public DataSource buildTemporaryDataSource(TenantRegistryRecord tenant) {
    return buildDataSource(tenant);
  }

  private HikariDataSource buildDataSource(TenantRegistryRecord tenant) {
    TenancyProperties.Pool pool = properties.getPool();
    HikariConfig config = new HikariConfig();
    config.setPoolName("tenant-" + tenant.tenantId());
    config.setJdbcUrl(tenant.dbUrl());
    config.setUsername(tenant.dbUsername());
    config.setPassword(credentialCipher.decrypt(tenant.encryptedDbPassword()));
    config.setMaximumPoolSize(pool.getMaximumPoolSize());
    config.setMinimumIdle(pool.getMinimumIdle());
    config.setConnectionTimeout(pool.getConnectionTimeoutMs());
    config.setIdleTimeout(pool.getIdleTimeoutMs());
    return new HikariDataSource(config);
  }

  private void enforcePoolLimit() {
    int maxTenantPools = properties.getPool().getMaxTenantPools();
    if (tenantRoutingDataSource.getRegisteredTenantCount() >= maxTenantPools) {
      throw new IllegalStateException(
          "Maximum tenant connection pool count reached: " + maxTenantPools);
    }
  }
}
