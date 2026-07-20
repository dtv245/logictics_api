package com.company.logicstic.tenant.dto;

import java.util.List;

public record TenantMigrationReport(
    boolean success,
    List<String> migratedTenants,
    List<String> pendingTenants,
    String failedTenant,
    String message) {

  public static TenantMigrationReport success(List<String> migratedTenants) {
    return new TenantMigrationReport(
        true, migratedTenants, List.of(), null, "All tenant migrations completed");
  }

  public static TenantMigrationReport failed(
      List<String> migratedTenants,
      List<String> pendingTenants,
      String failedTenant,
      String message) {
    return new TenantMigrationReport(false, migratedTenants, pendingTenants, failedTenant, message);
  }
}
