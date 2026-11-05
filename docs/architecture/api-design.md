# API Design — Hybrid Multi-Tenant

Status: target contracts captured from the user decision; implementation and OpenAPI changes not started. Existing endpoints must be checked before any addition.

## Authentication and context

| Operation | Contract | Security invariant |
|---|---|---|
| `GET /api/me/contexts` | Authenticated platform token; returns authorized company/context summaries and permitted display metadata | Membership, assignment, org active and registry tenant active are all backend-derived; no caller tenant list accepted |
| `POST /api/auth/context/select` | `{ orgUnitId }` -> operational token metadata/token response | Platform token only; verify membership, valid assignment/scope, org status and registry mapping; issue token for exactly one company; reject arbitrary tenant input |
| `GET /api/me` | Preserve current contract and compatibility during migration; define behavior by token type/version | Never resolve identity by caller subject/email/tenant parameters |

User requested additional provider-keyed Lark login/callback, refresh/logout. Before implementing, reconcile with existing `/api/auth/lark/authorize`, `/login`, `/callback`; avoid duplicate routes. Multi-provider state/redirect binding, CSRF/state, token audience/type, rotation and revocation need explicit design.

## Organization and access APIs

Proposed endpoints: `GET /api/organizations/tree`, `GET /api/organizations/{id}`, `POST /api/organizations`, `PUT /api/organizations/{id}`, `PATCH /api/organizations/{id}/status`, `GET /api/organizations/{id}/children`; roles/permissions/assignments endpoints as listed in the master requirement. Add pagination and validation for large hierarchies/assignments. Mutations require explicit control-plane permissions and audit records.

## Tenant/provider management

Proposed `GET/POST /api/tenants`, `POST /api/tenants/provision`, `GET /api/tenants/{id}`, status/test-connection endpoints; Lark provider CRUD as specified. Existing provisioning must be audited before reuse/exposure. Never return DB credentials or provider secrets; return secret configured/reference state only.

## Group reporting

Proposed overview/revenue/loads/documents/companies/sync-status endpoints. All require group-report permission and server-computed organization scope, bounded pagination/date filters, deterministic sorting, and `asOf`/last-sync metadata. Queries target reporting datasource only; no tenant fan-out.

## Error, pagination and compatibility

Reuse current `ApiResponse`, error envelope and pagination conventions after source verification. Distinguish 401 invalid token/type, 403 permission/scope, 404 inaccessible record where anti-enumeration applies, 409 membership/context conflict, 422 invalid org/action, 503 tenant/reporting dependency unavailable. Never return stack traces, credentials, tokens or raw SQL. No API has been implemented in this audit.
