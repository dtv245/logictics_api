---
description: "Multi-tenancy guidelines enforcing database isolation, tenant context resolution, and preventing cross-tenant leakage."
globs: "src/main/java/com/company/logicstic/**"
alwaysApply: true
---

# Multi-Tenancy Coding Guidelines (Database Isolation)

## Core Principles
1. **Tenant Context Resolution**: All database queries, repository calls, and cache operations must execute strictly within the active tenant context resolved via `TenantRoutingDataSource` and `TenantContextHolder`.
2. **No Hard-coded Tenant IDs**: Never hard-code tenant identifiers or bypass tenant routing in production business code.
3. **Cross-Tenant Access Forbidden**: Cross-tenant data access, queries without tenant boundaries, or shared database connections across different tenant requests are strictly prohibited.
4. **Tenant Lifecycle Management**: New tenant workspaces must be provisioned and migrated through `TenantProvisioningService` and `TenantMigrationService` using official Flyway tenant scripts (`classpath:db/migration/tenant`).
5. **Async & Background Context Propagation**: When spawning asynchronous threads, background jobs, or scheduled tasks, the tenant context MUST be explicitly passed and cleared in a `finally` block:
   ```java
   try {
       TenantContextHolder.setTenantId(targetTenantId);
       // execute tenant-bound task
   } finally {
       TenantContextHolder.clear();
   }
   ```
6. **Graceful Fallback**: If tenancy is disabled (`app.tenancy.enabled=false`), services must operate cleanly on the default standalone datasource.
