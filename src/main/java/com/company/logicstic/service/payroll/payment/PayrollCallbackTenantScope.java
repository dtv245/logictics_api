package com.company.logicstic.service.payroll.payment;
import com.company.logicstic.config.TenantContext;
import com.company.logicstic.service.TenantDataSourceService;
import com.company.logicstic.exception.ForbiddenException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.function.Supplier;
@Component
public class PayrollCallbackTenantScope {
 private final boolean tenancyEnabled;
 private final ObjectProvider<TenantDataSourceService> tenants;
 public PayrollCallbackTenantScope(@Value("${app.tenancy.enabled:false}") boolean tenancyEnabled,ObjectProvider<TenantDataSourceService> tenants) {
  this.tenancyEnabled=tenancyEnabled;this.tenants=tenants;
 }
 public <T> T withTenant(String verifiedTenantId,Supplier<T> operation) {
  if(!tenancyEnabled) return operation.get();
  if(verifiedTenantId==null || verifiedTenantId.isBlank()) throw new ForbiddenException("PAYMENT_CALLBACK_VERIFIED_TENANT_REQUIRED");
  var previous=TenantContext.getTenantId();
  if(previous.isPresent() && !previous.get().equals(verifiedTenantId)) throw new ForbiddenException("PAYMENT_CALLBACK_TENANT_MISMATCH");
  try {
   TenantContext.setTenantId(verifiedTenantId);
   var service=tenants.getIfAvailable();
   if(service==null) throw new ForbiddenException("PAYMENT_CALLBACK_TENANT_ROUTING_UNAVAILABLE");
   service.ensureTenantDataSource(verifiedTenantId);return operation.get();
  } finally {
   if(previous.isPresent()) TenantContext.setTenantId(previous.get());else TenantContext.clear();
  }
 }
}
