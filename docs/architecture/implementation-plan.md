# Implementation Plan — Hybrid Multi-Tenant

Status: Phase 0 audit completed and Maven baseline verified. Phase 1 schema work is gated on verifying the deployed Control/registry database identity and Flyway history. Preserve all backend/frontend dirty work and the active PAGINATION-001 work recorded in `.ai-workflow/PROJECT_MEMORY.md`.

| Phase | Slice | Dependencies / exit evidence |
|---|---|---|
| 0 Source audit | This document set; source, migration, auth, datasource, frontend, environment and deployment inventory | Docs reviewed; migration owners/version confirmed; baseline build/test on current source recorded; no live DB migration |
| 1 Control plane | Separate control DB config/Flyway; org tree; tenant registry mapping; provider-scoped identity; global user; memberships; roles/permissions/assignments; repository/service/API/tests | Additive migrations; tenant DBs unchanged; uniqueness/provider boundaries; unit + PostgreSQL tests; compatibility ledger |
| 2 Authentication | Platform token, contexts API, selection API, operational token; legacy Lark compatibility; tests | Valid membership/scope/tenant gates; token-type separation; no frontend tenant authority; old login remains until rollout evidence |
| 3 RBAC/scope | Resolver, permissions, materialized path descendant expansion, list/detail/write/export/search enforcement, audit | Negative cross-org and cross-tenant security tests on every sensitive API family; multiple assignment union and expiry coverage |
| 4 Reporting | reporting datasource/Flyway, fact/dimension schemas, tenant outbox, polling processor, retries/idempotency/checkpoints/replay/reconcile, group APIs | Real PostgreSQL integration; outage does not roll back tenant business writes; no fan-out; scope and sync timestamps verified |
| 5 Frontend | auth/context selection and operational token flow; org/access admin; group reports/sync status; errors/expiry | API contract tests and end-to-end switch/context flow; tenant caches isolated and stale requests cannot repopulate prior tenant |
| 6 Hardening | secrets, revocation, rate limits, observability, backup/recovery, performance and security review | Production readiness evidence; deployment/rollback and runbooks reviewed |

## Safe first implementation slice after Phase 0

First Phase 1 slice: verify deployed Control/registry DB identity and Flyway history, then add an isolated Control datasource/migration history and organization/tenant mapping scaffold. Do not modify existing tenant migrations, replace auth flow, run migration against an unidentified DB, or expose provisioning while identity/membership source and ownership rules remain undefined. This slice is currently gated because no live DB metadata/target was available during Phase 0.

## Risks / dependencies

1. Current worktrees are extensively dirty; changes must avoid overlap and tests/build outputs must not clobber checked-in generated artifacts.
2. Existing `LarkUserMapping` is tenant-local; global identity linking needs provider IDs and an explicit account-linking policy. Same email is not proof of same person.
3. Existing `tenant_registry` DDL is partly runtime-initialized and has no org mapping. Reconcile deployed registry schema before constraints/backfill.
4. Separate persistence units in current JPA app need careful repository/entity-manager isolation.
5. Tenant-local migrations already reach V53 in worktree. New control/reporting migrations use separate locations and histories; never guess sequence from tenant V53.
6. Group reporting facts require authoritative field mappings, event ordering and correction/deletion semantics per source entity.
7. No live database or production configuration was inspected. Migration run is prohibited until target DBs and backups are identified.
8. Backend repository is outside the current writable workspace; writing docs/code and running Maven requires explicit execution permission in this environment.
