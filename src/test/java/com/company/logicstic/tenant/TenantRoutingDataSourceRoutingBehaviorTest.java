package com.company.logicstic.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests pinning the CURRENT tenant-resolution contract of {@link
 * TenantRoutingDataSource}.
 *
 * <p>These tests describe behaviour as it is today, not as it ought to be. They exist so that a
 * future change to the resolution strategy fails loudly here instead of silently issuing a query
 * against the wrong tenant's database. In particular, replacing the fail-fast {@link
 * IllegalStateException} in {@link TenantRoutingDataSource#determineTargetDataSource()} with a soft
 * fallback (a default DataSource, {@code null}, or a lenient lookup key) MUST break these tests.
 *
 * <p>Both methods under test are {@code protected}; this test shares their package so it can call
 * them directly without reflection.
 */
class TenantRoutingDataSourceRoutingBehaviorTest {

  private final TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    routingDataSource.close();
  }

  // --- determineCurrentLookupKey(): soft, returns null when no tenant is bound ---

  @Test
  void lookupKeyIsBoundTenantIdWhenTenantIsBound() {
    TenantContext.setTenantId("tenant-a");

    assertThat(routingDataSource.determineCurrentLookupKey()).isEqualTo("tenant-a");
  }

  @Test
  void lookupKeyIsNullWhenNoTenantIsBound() {
    TenantContext.clear();

    assertThat(routingDataSource.determineCurrentLookupKey()).isNull();
  }

  // --- determineTargetDataSource(): fail-fast, never falls back ---

  @Test
  void targetDataSourceIsRegisteredInstanceForBoundTenant() {
    DataSource tenantDataSource = mock(DataSource.class);
    routingDataSource.addTenantDataSource("tenant-a", tenantDataSource);
    TenantContext.setTenantId("tenant-a");

    assertThat(routingDataSource.determineTargetDataSource()).isSameAs(tenantDataSource);
  }

  @Test
  void targetDataSourceThrowsWhenTenantIsNotRegistered() {
    routingDataSource.addTenantDataSource("tenant-a", mock(DataSource.class));
    TenantContext.setTenantId("tenant-b");

    assertThatThrownBy(() -> routingDataSource.determineTargetDataSource())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No DataSource registered for tenant")
        .hasMessageContaining("tenant-b");
  }

  @Test
  void targetDataSourceThrowsWhenNoTenantIsBound() {
    TenantContext.clear();

    assertThatThrownBy(() -> routingDataSource.determineTargetDataSource())
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * The sharpest guard against a soft fallback: even with a default target DataSource configured,
   * an unbound request must still fail fast. {@code AbstractRoutingDataSource} would normally fall
   * back to this default; the override deliberately does not. Removing the override, or adding a
   * default/lenient fallback to it, makes this test return the mock instead of throwing.
   */
  @Test
  void targetDataSourceDoesNotFallBackToDefaultWhenNoTenantIsBound() {
    DataSource fallback = mock(DataSource.class);
    routingDataSource.setDefaultTargetDataSource(fallback);
    routingDataSource.addTenantDataSource("tenant-a", mock(DataSource.class));
    TenantContext.clear();

    assertThatThrownBy(() -> routingDataSource.determineTargetDataSource())
        .isInstanceOf(IllegalStateException.class);
  }
}
