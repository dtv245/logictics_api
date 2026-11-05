# Control DataSource rollout

## Datasource and transaction ownership

When `app.tenancy.enabled=true`, the existing `tenantRegistryDataSource` bean is also exposed as `controlDataSource`. These are aliases for one Hikari pool, not two connection pools. `tenantRegistryJdbcTemplate` remains available for compatibility; `controlJdbcTemplate` is the explicit qualifier for new Control Plane JDBC work. Unqualified business JDBC injections resolve to `tenantJdbcTemplate`, which wraps the primary `TenantRoutingDataSource`.

| Work | Datasource | Transaction manager |
| --- | --- | --- |
| Business JPA repositories and services | Primary `TenantRoutingDataSource` | Primary `tenantTransactionManager` / `transactionManager` (`JpaTransactionManager`) |
| Control registry and future Control JDBC work | `controlDataSource` | `controlTransactionManager` (`DataSourceTransactionManager`) |
| Tenant schema migrations | Each tenant pool resolved by `TenantMigrationService` | Flyway manages its migration transaction |
| Control schema migrations | `controlDataSource`, registry location only | Flyway manages its migration transaction |

Control JDBC services must use `@Transactional(transactionManager = "controlTransactionManager")`. Business JPA services may continue using plain `@Transactional`; the tenant JPA manager remains `@Primary` because adding another transaction manager makes Spring Boot's transaction-manager auto-configuration back off.

## Migration ownership and legacy DDL

The application does not migrate Control schemas on startup. Tenant Flyway ownership remains with `TenantMigrationService` at `classpath:db/migration/tenant`. Control Flyway is a separately qualified, on-demand operation restricted to `classpath:db/migration/registry`; it has `cleanDisabled=true`, `baselineOnMigrate=false`, and `validateOnMigrate=true`.

`CONTROL_FLYWAY_EXECUTION_ENABLED` defaults to `false`. If explicitly enabled, a caller must still invoke `ControlFlywayMigrationService.migrateControlSchema()`; there is no `ApplicationRunner`, endpoint, or startup hook for Control migration. Prefer the separately managed Flyway runner using its dedicated migration role. Do not run the application service and external runner concurrently or switch ownership during a deployment without a maintenance window.

The legacy `TenantRegistryService.initializeRegistrySchema()` behavior remains enabled by default through `TENANT_REGISTRY_AUTO_INITIALIZE=true` for backward compatibility. To transfer schema ownership to Flyway, set `TENANT_REGISTRY_AUTO_INITIALIZE=false` before application startup. The on-demand Control migration service refuses to run while this legacy initializer is enabled. Do not enable both schema owners in the same environment.

The existing Registry V1 migration is unchanged. Tenant migrations V1–V53 are unchanged. Registry and tenant migration locations must remain separate.

## Safe rollout

1. Keep `TENANCY_ENABLED=false` in the existing application deployment until the separately approved Control/tenant datasource configuration is ready. This change does not authorize starting or restarting the application.
2. Take and verify a backup of the exact Control database before any future schema change. Use a disposable rehearsal target first.
3. Verify the database identity and Flyway history through read-only metadata queries. Never baseline, repair, or clean to make histories appear consistent.
4. Configure the registry connection using the existing `TENANT_REGISTRY_DB_URL`, `TENANT_REGISTRY_DB_USERNAME`, and `TENANT_REGISTRY_DB_PASSWORD` secret references. No credential values belong in source control.
5. For externally managed Control migrations, keep `CONTROL_FLYWAY_EXECUTION_ENABLED=false` and set `TENANT_REGISTRY_AUTO_INITIALIZE=false`; run only the registry Flyway location under the dedicated migration role after separate approval.
6. Enable tenancy only in a controlled deployment after the registry schema, Control connection, tenant migration process, Driver PII key configuration, and rollback plan have each been verified. The current backend startup incident caused by missing Driver PII keys remains an independent blocker to starting the application.
7. Roll back application configuration by disabling tenancy and restoring the prior legacy initialization flag only if that matches the existing schema ownership. Do not roll back by deleting schema objects, migration history, databases, or volumes.

## Known boundaries

- The Control DataSource reuses the existing registry pool and its existing connection properties. This change does not introduce separate application and migration credentials.
- The opt-in on-demand migration service must not be enabled in a normal application deployment if its registry connection uses a least-privilege runtime role. The external `control_migrator` process remains the preferred migration path.
- No live database, Docker service, Flyway command, or Spring Boot application was used by this implementation.
