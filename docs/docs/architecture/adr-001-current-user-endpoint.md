# ADR-001: Current-user identity to employee mapping

- **Status:** Implemented and verified
- **Date:** 2026-08-14
- **Verified:** 2026-08-15
- **Requirement:** FR-01, FR-02, FR-06, FR-11
- **Delivery task:** SLICE-001

## Context

The Spring service is an OAuth2 Resource Server. The external IdentityServer supplies `sub`, `email`, `tenant` and role claims, while several tenant workflows use an internal `employeeId`. The API currently asks clients to provide that ID for messaging and related operations, which cannot establish who the authenticated caller is.

The endpoint must expose authenticated identity context without adding login/session behavior or trusting a client-provided employee ID.

## Decision

Add authenticated `GET /api/me` with the existing `ApiResponse` envelope.

Response data:

```json
{
  "subject": "identity-subject",
  "email": "user@example.com",
  "tenantId": "tenant-claim",
  "roles": ["DISPATCHER"],
  "employeeId": "00000000-0000-0000-0000-000000000000"
}
```

`email` and `employeeId` are nullable. A valid identity without an employee row receives `200`, because platform and owner identities are not necessarily tenant employees. Missing/invalid authentication remains `401` through the production security chain.

### Boundary

- `identity/controller/CurrentUserController` owns the HTTP endpoint and envelope.
- `identity/service/CurrentUserService` maps trusted `JwtAuthenticationToken` claims and normalized `ROLE_` authorities into the response.
- `employee/service/CurrentEmployeeLookupService` is the public employee-module boundary for email-to-ID lookup.
- `employee/service/impl/CurrentEmployeeLookupServiceImpl` alone uses `EmployeeRepository`.

This avoids repository access across feature boundaries and avoids widening the high-impact generic `EmployeeService` interface.

### Trust and tenant rules

- Claims come only from Spring Security's validated JWT; no claim or employee ID is accepted from request input.
- The decoder already requires a nonblank tenant claim.
- When database-per-tenant mode is enabled, `TenantJwtClaimFilter` establishes the routed datasource before controller/service execution; employee lookup therefore stays in the selected tenant database.
- The lookup uses the current exact stored-email contract. Blank/missing email performs no query. Case normalization and case-insensitive uniqueness require a separate data-contract decision because the current create/update path and database uniqueness are case-sensitive.

### Authorization

No `SecurityConfiguration` change is required. The existing `/api/**` authenticated fallback protects `/api/me` and intentionally allows every authenticated role.

### Data and migration

No entity, table, index or Flyway change is required.

## Verification design

- Lookup service: mapped email, unknown email and blank email/no-query cases.
- Current-user service: claims, normalized sorted roles, mapped employee, unmapped employee and missing email.
- Controller slice with production security: authenticated `200` envelope and unauthenticated `401`; verify the trusted authentication is passed to the service.
- Full `./mvnw test` plus GitNexus `detect_changes` after implementation.

## Impact analysis

GitNexus upstream impact on 2026-08-14:

| Existing symbol | Risk | Direct dependents / processes | Decision |
|---|---|---|---|
| `EmployeeRepository` | MEDIUM | 6 direct importing files; 0 reported processes | Consume it from a new implementation; do not change its contract. |
| `EmployeeRepository.findByEmail` | LOW | 0 reported callers/processes | Reuse exact lookup without editing the method. |
| `SecurityConfiguration.securityFilterChain` | LOW | 0 reported callers/processes | Do not edit; authenticated fallback already applies. |
| `EmployeeService` | HIGH from prior catalog analysis | 17 reported dependents in the prior broad analysis | Do not extend or modify it. |

## Consequences

- Later messaging, notification, driver and document slices can resolve caller identity server-side.
- This slice does not itself remove client-supplied IDs from existing endpoints; those changes remain separate tasks with their own impact/security review.
- Exact email matching remains an explicit residual risk until identity provisioning and email normalization are specified end to end.

## Verification result

- Focused suite: 8 tests passed with no failures, errors or skips.
- Full suite: 163 unit/architecture tests passed.
- Integration/build gate: 28 integration tests passed under `./mvnw verify`, including PostgreSQL 18/Flyway V1-V3, REST API and Redis.
- Security diff review: complete coverage of all nine changed files, zero findings and zero deferred items.
- Independent code review: no defect; isolated patch contains nine additive files and 381 insertions, with no existing Java symbol modified.
