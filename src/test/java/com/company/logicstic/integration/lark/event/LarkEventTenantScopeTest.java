package com.company.logicstic.integration.lark.event;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.service.TenantDataSourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LarkEventTenantScopeTest {
  @AfterEach void clearTenant() { TenantContext.clear(); }

  @Test void bindsOnlyServerConfiguredTenantAndClearsAfterSuccessAndFailure() {
    var scope = LarkEventControllerTest.scope();
    assertEquals("event-test-tenant", scope.within(TenantContext::requireTenantId));
    assertTrue(TenantContext.getTenantId().isEmpty());
    assertThrows(IllegalStateException.class, () -> scope.within(() -> { throw new IllegalStateException(); }));
    assertTrue(TenantContext.getTenantId().isEmpty());
    TenantContext.setTenantId("event-test-tenant");
    scope.within(() -> null);
    assertEquals("event-test-tenant", TenantContext.requireTenantId());
  }

  @Test void rejectsCrossTenantContextAndFailsClosedWithoutRoutingService() {
    var beans = new StaticListableBeanFactory();
    var scope = new LarkEventTenantScope(properties(TOKEN, ""), true, beans.getBeanProvider(TenantDataSourceService.class));
    assertEquals(503, assertThrows(ApiException.class, () -> scope.within(() -> null)).getStatus().value());
    TenantContext.setTenantId("another-tenant");
    assertEquals(401, assertThrows(ApiException.class, () -> scope.within(() -> null)).getStatus().value());
    assertEquals("another-tenant", TenantContext.requireTenantId());
  }

  @Test void preparesConfiguredTenantWhenRoutingIsEnabled() {
    var tenants = mock(TenantDataSourceService.class);
    var beans = new StaticListableBeanFactory();
    beans.addBean("tenants", tenants);
    var scope = new LarkEventTenantScope(properties(TOKEN, ""), true, beans.getBeanProvider(TenantDataSourceService.class));
    scope.within(() -> null);
    verify(tenants).ensureTenantDataSource("event-test-tenant");
    assertTrue(TenantContext.getTenantId().isEmpty());
  }
}
