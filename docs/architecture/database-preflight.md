# Database Preflight — Registry DB / Phase 1

**Date:** 2026-11-04 07:49 +07:00  
**Decision:** Reuse the existing Registry Database as Control Database; do not create a new Control Database.  
**Result:** **NOT READY FOR PHASE 1** — the configured Registry DB and its Flyway history cannot be identified from the current runtime configuration.

## 1. Worktree preservation

Before this preflight, `git status --porcelain` reported 465 entries: 104 tracked modifications, 7 staged deletions, and 354 untracked entries. The backend already contains extensive unrelated work, including tenant migrations V41–V53 and prior documentation. No existing source, migration, configuration, or data was changed by this preflight. This document and the workflow handoff are the only intended repository writes for this turn.

## 2. Registry DataSource

The source defines `tenantRegistryDataSource` in `TenantDataSourceConfiguration`; the bean is conditional on `app.tenancy.enabled=true`. URL, username, and password bind to `TENANT_REGISTRY_DB_URL`, `TENANT_REGISTRY_DB_USERNAME`, and `TENANT_REGISTRY_DB_PASSWORD`. The application config has an empty URL fallback and `TENANCY_ENABLED` defaults to `false`.

| Source | Registry URL present | Tenancy enabled | Registry DB name |
|---|---:|---:|---|
| Root `.env` | No | `false` | Not resolvable |
| Root `.env.example` | No | Not specified | Not resolvable |
| Effective root Compose API service | No | Not passed; app default is `false` | Not resolvable |
| Running `logistics-api` container | No | Not present; app default is `false` | Not resolvable |

The application profile inventory contains `application.yml`, `application-nodb.yml`, and test profile `application-test.yml`. No dev/prod profile file is present in `src/main/resources`. The `nodb` profile excludes datasource and Flyway auto-configuration. No effective Registry URL was found in the checked profiles or root Compose configuration.

## 3. Database name resolution and Docker mismatch

| Setting | Resolved database name | Evidence |
|---|---|---|
| Root `.env` `DB_URL` | `logistics` | Parsed JDBC path; credentials were not emitted |
| `.env.example` `DB_URL` | `us_logisticsx` | Parsed JDBC path |
| Effective root Compose API `DB_URL` and `SPRING_DATASOURCE_URL` | `us_logisticsx` | Inline API URL defaults to `${POSTGRES_DB:-us_logisticsx}` |
| Effective root Compose PostgreSQL `POSTGRES_DB` | `logistics` | PostgreSQL service defaults to `${POSTGRES_DB:-logistics}`; `POSTGRES_DB` is absent from root `.env` |
| Running root Compose PostgreSQL container | `logistics` | Container metadata only |
| Running root Compose API container | `us_logisticsx` | Database-name extraction from container environment only |

The Compose API URL overrides root `.env` `DB_URL`; the Compose defaults disagree. Both `logistics` and `us_logisticsx` currently exist in the root PostgreSQL instance, so this establishes the mismatch but not when/how the second database was created. A fresh Compose database volume may initialize only `logistics` while the API targets `us_logisticsx`.

Another running PostgreSQL container advertises `master_logisticsx`, but belongs to a different Compose project/path. It has neither `public.tenant_registry` nor `public.flyway_schema_history`; it is not established as this app's Registry DB and was not treated as the target.

## 4. Flyway configuration and source migrations

- `spring.flyway.enabled` is controlled by `SPRING_FLYWAY_ENABLED` (`true` in root `.env` and effective root Compose API service).
- `spring.flyway.locations` is `classpath:db/migration/tenant` in root `.env` and effective root Compose API service; YAML fallback is also tenant-only.
- `TenantMigrationService` uses `app.tenancy.migration.locations` (default `classpath:db/migration/tenant`) for active tenant databases.
- The only Registry migration in source is `src/main/resources/db/migration/registry/V1__create_tenant_registry.sql`.
- No dedicated Registry/Control Flyway bean, Registry migration location, or configured Registry history was found. A test explicitly migrates Registry V1; that is not evidence of a production/dev Registry migration.
- `TenantRegistryService` can create `tenant_registry` at startup with `CREATE TABLE IF NOT EXISTS` when tenancy is enabled and `TENANT_REGISTRY_AUTO_INITIALIZE=true`. This runtime DDL is separate from Registry Flyway history.

## 5. Read-only database checks

Only metadata `SELECT` queries were used. No application/business rows were read.

| Database inspected | `public.tenant_registry` | `public.flyway_schema_history` | Interpretation |
|---|---|---|---|
| `logistics` (root Compose PostgreSQL) | Absent | Present | Main/tenant DB candidate; not proven Registry DB |
| `us_logisticsx` (root Compose PostgreSQL) | Absent | Present | Main/tenant DB candidate; not proven Registry DB |
| `master_logisticsx` (separate dev PostgreSQL container) | Absent | Absent | Different Compose project; not proven Registry DB |

Because no configured or confirmed Registry DB could be identified, Registry history was not queried for version rows. Highest applied Registry version, pending migrations, failed migrations, and checksum mismatches are **not verifiable**. The absence of `tenant_registry` in inspected application DBs does not establish the state of an unidentified external Registry DB.

## 6. Risks

1. Registry access is disabled/unconfigured in the checked runtime; its target cannot be resolved from inspected environment.
2. Main database naming differs across `.env`, `.env.example`, PostgreSQL bootstrap, and Compose API JDBC URL.
3. Standard Spring Flyway points at tenant migrations, while Registry V1 is in a separate source location without Registry Flyway runtime configuration.
4. Optional startup DDL can create the Registry table outside Flyway history; reconcile this before adding versioned Control schema.
5. The worktree is heavily dirty; preserve every existing change and migration.

## 7. Readiness decision and information needed

**NOT READY FOR PHASE 1.** Reusing Registry DB is confirmed, but the actual target database and its migration history are not verified. Do not number or apply a Control/Organization migration yet.

To unblock this read-only preflight, make the existing Registry endpoint available through the current secret-managed environment (do not send passwords/secrets) and identify its host/service and database name. The application expects `TENANT_REGISTRY_DB_URL`, `TENANT_REGISTRY_DB_USERNAME`, and `TENANT_REGISTRY_DB_PASSWORD`; credentials can remain local. Then query `tenant_registry` and `flyway_schema_history` and compare history/checksums to source without running migration.
