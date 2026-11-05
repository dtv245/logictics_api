# Architecture Decisions — Hybrid Multi-Tenant

## ADR-003 — Hybrid control, operational and reporting planes

- **Status:** ACCEPTED by the user in the master architecture request (2026-11-04).
- **Context:** Existing code already routes operational requests to per-tenant databases but lacks a real central identity/control model and central group reporting.
- **Decision:** Preserve one operational DB per company and current routing; add a Control DB for organization/identity/membership/access/registry/audit and a distinct Reporting DB for projections/group reporting. Implement group reporting from central facts, never fan-out queries.
- **Relationship to ADR-001:** Supersedes its statement that Database-per-Tenant is the entire target and its proposed broad “Master DB” contents. Retains the physical database-per-tenant decision. Registry is part of control plane, but existing runtime registry schema is not yet a complete control DB.
- **Relationship to ADR-002:** Preserves Flyway immutability and sole schema ownership. New persistence planes require separate migration locations/history. Never edit deployed tenant V1–V53 or registry V1.
- **Consequences:** Three explicit DataSources and transaction managers; platform/operational token boundary; org-scoped access service; business outbox and idempotent reporting projection; migration and rollout must be additive and feature-flagged.
- **Alternatives rejected:** Shared operational database; query fan-out across tenant DBs; rewriting existing tenant routing/auth/business services.

## Fixed policy decisions from user

1. Group admins select a company/context for operational work; each operational request routes to one tenant DB.
2. Each company owns a distinct Lark tenant/provider configuration.
3. Group reporting is an initial capability backed by central reporting DB.
4. Platform token cannot authorize tenant business repositories; operational token represents exactly one backend-authorized tenant/context.
5. Scope comes only from control-plane membership/assignments and organization hierarchy, with UNION semantics for active valid assignments.
6. Do not use email equality as global identity proof; external identity uniqueness includes identity provider.
7. Keep the old auth path until new path and compatibility are verified; no production rollout is authorized here.

## Open decisions / assumptions

- Authority and linking workflow for existing tenant-local Lark mappings into global users.
- Control DB hosting/credentials/backup, migration deployment ownership and environment names.
- Operational token issuer/key/audience/TTL/refresh/revocation contract and browser storage strategy.
- Status/tenant deletion/retention policies; org code uniqueness and path format.
- Domain owner/executor semantics and authoritative backfill source per business table.
- Outbox payload minimization, update/delete correction semantics and replay/retention windows.
- Reporting fact grain, currency/time-zone policy, delayed/late events and source correction strategy.
- Identity-provider API for memberships/context selection per Lark app/tenant.

No code, migration, secret, runtime environment, database, or deployed service was changed as part of the audit draft.
