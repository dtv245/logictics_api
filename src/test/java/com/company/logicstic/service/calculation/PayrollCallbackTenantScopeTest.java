package com.company.logicstic.service.calculation;
import com.company.logicstic.service.payroll.payment.PayrollCallbackTenantScope;
import com.company.logicstic.config.TenantContext;
import com.company.logicstic.service.TenantDataSourceService;
import com.company.logicstic.exception.ForbiddenException;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PayrollCallbackTenantScopeTest {
 @SuppressWarnings("unchecked") private ObjectProvider<TenantDataSourceService> provider() {return mock(ObjectProvider.class);}
 @Test void anonymousCallbackCannotInferDefaultTenant() {
  TenantContext.clear();var tenants=provider();var scope=new PayrollCallbackTenantScope(true,tenants);
  assertThrows(ForbiddenException.class,() -> scope.withTenant(null,() -> "posted"));
  verify(tenants,never()).getIfAvailable();assertTrue(TenantContext.getTenantId().isEmpty());
 }
 @Test void authenticatedTenantCannotBeReboundToDifferentVerifiedCompany() {
  TenantContext.setTenantId("tenant-a");
  try {
   var tenants=provider();var scope=new PayrollCallbackTenantScope(true,tenants);
   assertThrows(ForbiddenException.class,() -> scope.withTenant("tenant-b",() -> "posted"));
   assertEquals("tenant-a",TenantContext.requireTenantId());verify(tenants,never()).getIfAvailable();
  } finally {TenantContext.clear();}
 }
 @Test void bindsVerifiedRegisteredTenantBeforeDatabaseOperationAndRestoresScopeOnFailure() {
  TenantContext.clear();var tenants=provider();var service=mock(TenantDataSourceService.class);
  when(tenants.getIfAvailable()).thenReturn(service);var scope=new PayrollCallbackTenantScope(true,tenants);
  assertThrows(IllegalStateException.class,() -> scope.withTenant("verified-company",() -> {
   verify(service).ensureTenantDataSource("verified-company");assertEquals("verified-company",TenantContext.requireTenantId());
   throw new IllegalStateException("fixture rollback");
  }));
  assertTrue(TenantContext.getTenantId().isEmpty());
 }
}
