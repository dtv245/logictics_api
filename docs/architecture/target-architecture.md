# Target Architecture — Hybrid Multi-Tenant

Status: target fixed by user request; implementation not yet started.

## Decision

Keep one operational PostgreSQL database per company. Add a Central Control Database and a separate Central Reporting Database. Do not consolidate tenant DBs and do not fan-out group reports to tenant databases.

```text
Frontend
  -> Spring Security / API
       -> Control plane: identity, organization, membership, access, tenant registry, audit
       -> Operational plane: validated Operational Token -> TenantContext -> existing TenantRoutingDataSource -> exactly one company DB
       -> Reporting plane: validated report scope -> reporting datasource -> central facts

Tenant transaction -> tenant outbox -> poller/retry/idempotent projector -> reporting DB
```

## DataSource boundaries

1. `controlDataSource`: control-plane entities/repositories and control transaction manager. Must not be the tenant-routed JPA datasource.
2. Existing `tenantRoutingDataSource`: operational repositories only; one authenticated `tenant_id` per request. Preserve strict lookup and `TenantContext` cleanup.
3. `reportingDataSource`: reporting projection repository and reporting transaction manager. Operational repositories must not query it.

Use explicit `@Qualifier`, entity-manager factory, transaction-manager, and Flyway configuration for each persistence unit. No repository package may bind ambiguously to the primary datasource.

## Identity and operational context

- Platform authentication resolves a provider-scoped external identity to a global user and active memberships in the control DB.
- `/api/me/contexts` returns only contexts authorized by backend membership, assignments, org status and tenant status.
- Context selection verifies requested org unit against authenticated membership and effective scope, resolves the company tenant from registry, then issues an operational token. Never trust frontend tenant IDs as authorization.
- The operational token carries `token_type=OPERATIONAL`, `tenant_id`, `active_org_unit_id`, membership ID, subject, optional tenant-local employee ID, `jti`, expiry, and compact role/permission references. It does not carry arbitrary client scopes.
- Preserve legacy Lark token flow behind a migration/feature flag until new identity mapping, callback, context selection and compatibility tests pass. Do not silently convert email equality into global identity.

## Scope and organization

Control DB owns GROUP/COMPANY/BRANCH/DEPARTMENT hierarchy, materialized path, tenant-to-company mapping, users, identities, memberships, roles, permissions, scoped assignments and central audit. Effective permissions and scopes are calculated server-side. Multiple active valid assignments union their descendants. One operational request routes to one company database; group reporting reads central facts only.

Business ownership is added only to confirmed tenant entities where organization-level data partition is meaningful. Distinguish owner from executor for shared operations. Preserve existing tenant isolation as an outer boundary.

## Reporting and events

- Central reporting database has its own migrations and transaction boundary.
- Each tenant DB writes a business outbox record atomically with the source transaction. A poller projects selected events into reporting facts with source identity idempotency keys, retries, checkpointing, stuck-event detection, replay and reconciliation.
- Reporting outage must not roll back the operational transaction. Projection failures remain observable and retryable.
- Group report authorization is evaluated in control plane before querying reporting facts. Scope predicates are server-derived; responses include last sync time and pagination/time filters where applicable.

## Compatibility and rollout

- Existing `TenantContext`, `TenantRoutingDataSource`, JWT filter, provisioning and business controllers remain in place. Introduce new paths alongside old ones.
- Roll out control schema and read-only context endpoints first. Establish explicit identity mapping and memberships before issuing new operational tokens. Use feature flags/dual-read only where they do not permit cross-tenant access.
- Keep old Lark callback until new flow is demonstrated end-to-end. Never enable a partially configured control/reporting datasource in existing deployments.
- No production migration or credential/config change is included in this architecture audit.
