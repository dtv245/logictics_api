# Database Design — Hybrid Multi-Tenant

## Current facts

- `registry/V1__create_tenant_registry.sql`: tenant_id PK; DB URL, username, password, status; no org ID/timestamps. Runtime can also `CREATE TABLE IF NOT EXISTS` when auto-initialize is enabled.
- Tenant schema Flyway currently includes V1–V53 in the worktree. ADR-002 makes Flyway the sole schema owner; V1–V53 are immutable once deployed.
- Existing tenant V1 has employees, loads, invoices, documents and other business tables. Current report services operate against the tenant DB.
- No control schema or reporting schema/history was found. No live database introspection was performed.

## Proposed ownership map (design only; no migration authored)

### Control database

| Table | Purpose / key constraints |
|---|---|
| organization_units | UUID PK; parent FK; code/name/type/status; materialized `path` and `depth`; unique code within parent/scope; indexes on parent, path and status |
| tenant_registry | Preserve existing tenant_id and encrypted credentials; add nullable org_unit_id FK to COMPANY; timestamps/status; unique org_unit_id where one company maps to one tenant; rollout/backfill before NOT NULL |
| identity_providers | Provider scoped to company; provider_type/app ID/secret reference/callback/status; secret value remains external secret reference |
| global_users | UUID PK, display/contact/status/timestamps; email is not a global identity key absent an approved policy |
| external_identities | UUID PK; global user/provider FKs; open_id/union_id/email/status; UNIQUE(provider_id, open_id); index global user |
| tenant_memberships | UUID PK; global user + tenant FKs; logical employee ID; status/join/left times; unique active membership policy to be chosen after data audit |
| roles / permissions / role_permissions | Stable codes and unique constraints; role-permission join PK; seeded permissions versioned and auditable |
| employee_role_assignments | UUID PK; global user, tenant, role, scope org FKs; valid interval/status; indexes by user+tenant+status and scope |
| audit_logs | UUID/sequence PK; actor/action/resource/tenant/scope/result/time/metadata; retention and sensitive-field policy required |

The unique/open-ended policies above need source data analysis before enforcing. Control DB migrations must use their own Flyway location/history and transaction manager.

### Tenant databases

Retain all current operational tables. Add `owning_org_unit_id` only to confirmed company/branch-owned records. Initial candidates for domain review: loads, documents, invoices, employees and reportable source aggregates. Do not bulk-add columns: decide ownership per entity and backfill from verified tenant-to-company mapping. Use `executing_org_unit_id` only for confirmed cross-company execution semantics. Tenant DB remains physical company isolation.

### Reporting database

Separate Flyway and datasource. Proposed `dim_organizations`, `dim_time`, `dim_employees`, `fact_loads`, `fact_documents`, `fact_invoices`, `sync_checkpoints`, `report_audit_logs`. Every fact has source tenant + source record identity for idempotency, org keys where source truth exists, and sync time. Do not invent fields or populate from unverified assumptions. Add indexes around company/path, date, status and source identity after query contract is finalized.

### Outbox

Tenant-local `outbox_events`: event ID, event type/version, aggregate type/ID, occurred time, payload or immutable reference, attempts, next retry, processing status/lease, last error code, and creation time. Atomic insert with business transaction. Reporting projection uniqueness on `(source_tenant_id, source_event_id)` or equivalent source record/version; checkpoint must not be sole duplicate defense.

## Migration and recovery

- Never edit deployed V1–V53 tenant files or registry V1. New control/reporting histories are separate; do not assign versions until actual deployed schemas/history are inspected.
- Add nullable mapping columns, backfill in bounded batches, verify coverage/uniqueness, then enforce constraints in a later migration.
- Take verified backup/restore point before registry/data backfill. Prefer forward-fix; do not delete business data. Document rollback as disabling new routes/feature flags and retaining additive schema, not dropping tables.
- No migration has been run and no production database is authorized for migration.
