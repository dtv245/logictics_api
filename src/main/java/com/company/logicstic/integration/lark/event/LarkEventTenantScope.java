package com.company.logicstic.integration.lark.event;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.service.TenantDataSourceService;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LarkEventTenantScope {
  private final LarkProperties properties;
  private final boolean tenancyEnabled;
  private final ObjectProvider<TenantDataSourceService> tenants;

  public LarkEventTenantScope(LarkProperties properties,
      @Value("${app.tenancy.enabled:false}") boolean tenancyEnabled,
      ObjectProvider<TenantDataSourceService> tenants) {
    this.properties = properties;
    this.tenancyEnabled = tenancyEnabled;
    this.tenants = tenants;
  }

  public <T> T within(Supplier<T> operation) {
    String tenant = properties.defaultTenantId();
    var previous = TenantContext.getTenantId();
    if (previous.filter(bound -> !bound.equals(tenant)).isPresent()) throw LarkEventSecurity.unauthorized();
    try {
      // Routing is server-owned, never taken from event.tenant_key or HTTP input.
      TenantContext.setTenantId(tenant);
      if (tenancyEnabled) {
        var service = tenants.getIfAvailable();
        if (service == null) throw LarkEventSecurity.unavailable();
        try {
          service.ensureTenantDataSource(tenant);
        } catch (RuntimeException unavailable) {
          throw LarkEventSecurity.unavailable();
        }
      }
      return operation.get();
    } finally {
      if (previous.isPresent()) TenantContext.setTenantId(previous.get());
      else TenantContext.clear();
    }
  }
}
