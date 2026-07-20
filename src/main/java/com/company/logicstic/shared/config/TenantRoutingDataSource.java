package com.company.logicstic.shared.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class TenantRoutingDataSource extends AbstractRoutingDataSource implements AutoCloseable {

  private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

  public TenantRoutingDataSource() {
    setTargetDataSources(Map.of());
    setLenientFallback(false);
  }

  @Override
  protected Object determineCurrentLookupKey() {
    return TenantContext.getTenantId().orElse(null);
  }

  @Override
  protected DataSource determineTargetDataSource() {
    String tenantId = TenantContext.requireTenantId();
    DataSource dataSource = tenantDataSources.get(tenantId);
    if (dataSource == null) {
      throw new IllegalStateException("No DataSource registered for tenant " + tenantId);
    }
    return dataSource;
  }

  public Optional<DataSource> getRegisteredDataSource(String tenantId) {
    return Optional.ofNullable(tenantDataSources.get(tenantId));
  }

  public int getRegisteredTenantCount() {
    return tenantDataSources.size();
  }

  public synchronized void addTenantDataSource(String tenantId, DataSource dataSource) {
    tenantDataSources.put(tenantId, dataSource);
    Map<Object, Object> targetDataSources = new HashMap<>(tenantDataSources);
    setTargetDataSources(targetDataSources);
    afterPropertiesSet();
  }

  @Override
  public void close() {
    tenantDataSources
        .values()
        .forEach(
            dataSource -> {
              if (dataSource instanceof AutoCloseable closeable) {
                try {
                  closeable.close();
                } catch (Exception ignored) {
                  // Best effort shutdown for connection pools.
                }
              }
            });
    tenantDataSources.clear();
  }
}
