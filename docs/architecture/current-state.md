# Current State Audit — Hybrid Multi-Tenant

Audit date: 2026-11-04. Source inspected in `/home/vumoi/logictics_api` and `/home/vumoi/logictics-app`. No database was connected and no migration was run.

## Repository and runtime

- Backend: Maven, Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security OAuth2 resource server, Spring Data JPA, PostgreSQL, Flyway, HikariCP, JJWT 0.12.6.
- Frontend: React 18/TypeScript/Vite/Refine v4/React Router 6/Ant Design 5/TanStack Query 4.
- Backend has dirty tracked and untracked files, including tenant migrations V41–V53 and substantial source/test changes. Preserve all of them. The prior workflow memory tracks another active task; do not replace its history.
- `docker-compose.yml` describes one PostgreSQL service and Redis. Its application DB default (`us_logisticsx`) differs from the PostgreSQL service DB default (`logistics`). No separate reporting DB is configured.

## Verified backend flow

```text
Lark OAuth callback
 -> LarkAuthService.handleCallback
 -> resolveLoginTenant (LARK_DEFAULT_TENANT_ID)
 -> tenant-local LarkUserMapping / Employee
 -> internal JWT (subject, email, tenant, roles, employee_id)
 -> LarkAuthenticationFilter / Spring Security
 -> TenantJwtClaimFilter (when tenancy enabled)
 -> TenantContext -> TenantRoutingDataSource -> tenant DB
```

- `LarkAuthService` resolves one tenant using configured default tenant and verifies/maps employee inside that tenant DB. Role is the tenant-local Employee role. JWT contains tenant and role claims; there are no platform and operational token types.
- `TenantContext` is a ThreadLocal with `remove()` cleanup in the tenant filter's `finally`; Lark callback restores its previous context. Tenant filter reads the authenticated `tenant` claim, checks active registry entry, ensures the data source, and fails closed on missing/inactive/unavailable tenant.
- `TenantRoutingDataSource` has strict no-fallback routing and a bounded dynamic Hikari pool. `TenantDataSourceService` rechecks registry status even for cached pools.
- `TENANCY_ENABLED` defaults false. With tenancy enabled, `tenantRoutingDataSource` is primary; `tenantRegistryDataSource` and JdbcTemplate are separate. This is a tenant registry connection, not yet a general control-plane persistence architecture. No reporting datasource/transaction manager exists.
- `tenant_registry` exists in registry migration V1 and is also optionally initialized with runtime DDL. It stores tenant ID, DB URL/user, encrypted DB password, status; it has no organization mapping or timestamps in current migration.
- Tenant migrations are `src/main/resources/db/migration/tenant/V1..V53`; registry migrations currently contain V1. Flyway is the schema owner per ADR-002. V41–V53 are untracked in the current worktree: do not renumber or edit them.
- Registry credential encryption uses AES-GCM via `TenantCredentialCipher`, configured by `TENANT_REGISTRY_ENCRYPTION_KEY`.
- Tenant provisioning creates a PostgreSQL database, runs tenant migrations, then inserts registry credentials. The provisioning endpoint/service and validation/authz require separate security review before exposing company creation.
- Current `/api/me` resolves identity against the current tenant-local Employee table only. Frontend `createCurrentUserLoader` synthesizes `tenants: [currentTenant]`; `useTenantList` therefore cannot list memberships. `useSwitchTenant` clears tenant-scoped query cache and starts forced authentication; it cannot switch tenant in-process.
- `LarkUserMapping` is stored per tenant and unique by open_id and union_id in that tenant database. It is not provider-scoped global identity.
- Existing `/api/reports/**` and calculation/report services use the active tenant datasource. No central reporting database, reporting Flyway location, facts/dimensions, or group reporting endpoints were found.
- No organization unit hierarchy, memberships, scoped role assignment, central audit control plane, tenant business outbox, or group data-scope service was found. Existing Lark event inbox/outbox-like receipts are integration-specific and are not a reporting outbox.
- Tenant business schema is a broad legacy baseline plus immutable migrations. `employees`, `loads`, `invoices`, and `documents` exist in tenant migrations; company/branch ownership columns are not currently represented as organization scope fields.

## Existing decisions and conflicts

- ADR-001 accepts `DATABASE_PER_TENANT` and describes a future “Master DB” containing more than the registry; current source implements only a separate registry datasource, not that full master/control database.
- ADR-002 accepts Flyway as schema owner and says migrations are immutable after deployment. New control/reporting schemas need separate Flyway locations/history and DB ownership; tenant V1–V53 must remain unchanged.
- User decision in this task supersedes ADR-001 only at the system-architecture level: retain operational database-per-company and add control and reporting planes. It does not authorize merging tenant databases.

## Current risks / evidence limits

- Both repositories have extensive uncommitted work. No clean baseline can be inferred from HEAD or the older workflow memory.
- No live schema/database was inspected; table facts above derive from migration/source files.
- Baseline verified on this dirty source: ./mvnw test exited 0; 894 tests, 0 failures, 0 errors, 232 skipped. This is a current-source test baseline, not proof of deployed DB migration or production readiness.
- Platform identity and memberships have no authoritative source/data migration mapping yet. Do not backfill users by email alone or enable new auth tokens until identity-provider and user-link policy is approved.
- Scope-bearing domain data and reporting facts require field-by-field domain review; no blanket ownership-column migration is safe.
