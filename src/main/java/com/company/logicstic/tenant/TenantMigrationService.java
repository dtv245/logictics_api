package com.company.logicstic.tenant;

import com.company.logicstic.shared.config.TenancyProperties;
import com.company.logicstic.tenant.dto.TenantMigrationReport;
import com.company.logicstic.tenant.dto.TenantRegistryRecord;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantMigrationService {

  private final TenantRegistryService tenantRegistryService;
  private final TenantDataSourceService tenantDataSourceService;
  private final TenancyProperties properties;

  public TenantMigrationReport migrateAllActiveTenants() {
    List<TenantRegistryRecord> tenants = tenantRegistryService.findAllActiveTenants();
    List<String> migratedTenants = new ArrayList<>();

    for (int index = 0; index < tenants.size(); index++) {
      TenantRegistryRecord tenant = tenants.get(index);
      try {
        migrateTenant(tenant);
        migratedTenants.add(tenant.tenantId());
        log.info("Tenant migration completed for tenant={}", tenant.tenantId());
      } catch (RuntimeException ex) {
        List<String> pendingTenants =
            tenants.subList(index, tenants.size()).stream()
                .map(TenantRegistryRecord::tenantId)
                .toList();
        log.error(
            "Tenant migration failed for tenant={}. Migrated tenants={}, pending tenants={}",
            tenant.tenantId(),
            migratedTenants,
            pendingTenants,
            ex);
        return TenantMigrationReport.failed(
            List.copyOf(migratedTenants), pendingTenants, tenant.tenantId(), ex.getMessage());
      }
    }

    return TenantMigrationReport.success(List.copyOf(migratedTenants));
  }

  public void migrateTenant(TenantRegistryRecord tenant) {
    DataSource dataSource = tenantDataSourceService.buildTemporaryDataSource(tenant);
    try {
      Flyway.configure()
          .dataSource(dataSource)
          .locations(properties.getMigration().getLocations().toArray(String[]::new))
          .cleanDisabled(true)
          .load()
          .migrate();
    } catch (Exception ex) {
      throw new IllegalStateException("Migration failed for tenant " + tenant.tenantId(), ex);
    } finally {
      if (dataSource instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception ignored) {
          // Best effort shutdown for temporary migration pool.
        }
      }
    }
  }
}
