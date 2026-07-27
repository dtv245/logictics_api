package com.company.logicstic.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cache layer's security boundary.
 *
 * <p>LogisticsX is database-per-tenant, so the same UUID is a different row in every tenant
 * database. If the key prefix ever loses its tenant segment, company A is served company B's data
 * with no error anywhere — which is why the fail-closed case below matters more than the happy
 * path.
 */
@DisplayName("TenantAwareCacheKeyPrefix")
class TenantAwareCacheKeyPrefixTest {

  private static final String APP_PREFIX = "logistics";

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("scopes the key by tenant when multi-tenancy is on")
  void includesTheBoundTenant() {
    TenantContext.setTenantId("acme");

    assertThat(new TenantAwareCacheKeyPrefix(APP_PREFIX, true).compute("terminal"))
        .isEqualTo("logistics:acme:terminal:");
  }

  @Test
  @DisplayName("two tenants never share a key prefix for the same cache")
  void differentTenantsGetDifferentPrefixes() {
    TenantAwareCacheKeyPrefix prefix = new TenantAwareCacheKeyPrefix(APP_PREFIX, true);

    TenantContext.setTenantId("acme");
    String acme = prefix.compute("customer");
    TenantContext.setTenantId("swift");
    String swift = prefix.compute("customer");

    assertThat(acme).isNotEqualTo(swift);
  }

  @Test
  @DisplayName("fails closed when multi-tenancy is on but no tenant is bound")
  void refusesToGuessATenant() {
    TenantAwareCacheKeyPrefix prefix = new TenantAwareCacheKeyPrefix(APP_PREFIX, true);

    // The tempting alternative — getTenantId().orElse("default") — looks correct in single-tenant
    // mode and starts serving one company's rows to another the day TENANCY_ENABLED flips to true.
    assertThatThrownBy(() -> prefix.compute("terminal"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No tenant is bound");
  }

  @Test
  @DisplayName("uses a fixed scope when multi-tenancy is off, and never the word 'default'")
  void singleTenantModeUsesAFixedScope() {
    String computed = new TenantAwareCacheKeyPrefix(APP_PREFIX, false).compute("role");

    assertThat(computed).isEqualTo("logistics:single:role:");
    assertThat(computed).doesNotContain("default");
  }

  @Test
  @DisplayName("single-tenant mode ignores a leaked ThreadLocal rather than keying on it")
  void singleTenantModeIgnoresAStaleThreadLocal() {
    TenantContext.setTenantId("leftover");

    assertThat(new TenantAwareCacheKeyPrefix(APP_PREFIX, false).compute("role"))
        .isEqualTo("logistics:single:role:");
  }

  @Test
  @DisplayName("the cache name is part of the prefix, so two caches cannot collide")
  void separatesCachesFromEachOther() {
    TenantAwareCacheKeyPrefix prefix = new TenantAwareCacheKeyPrefix(APP_PREFIX, false);

    assertThat(prefix.compute("employee")).isNotEqualTo(prefix.compute("truck"));
  }
}
