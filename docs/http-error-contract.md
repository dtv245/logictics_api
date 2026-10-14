# HTTP error contract

Implementation: 2026-10-13, Spring Boot 4.1.1 / MVC 7.0.9. Applies to the source changes in the approved exception remediation plan. Deploy the compatible frontend before the backend.

## Envelope

```json
{
  "success": false,
  "code": "DATA_INTEGRITY_VIOLATION",
  "message": "Cannot complete request due to a data conflict",
  "data": null,
  "errors": [{"field": "number", "code": "UNIQUE", "message": "This value is already in use"}],
  "meta": {"timestamp": "2026-10-13T00:00:00Z", "path": "/api/trucks", "requestId": "request-fixture"},
  "params": {}
}
```

- New failure envelopes include a root `params` object, even when empty. Success envelopes retain their previous shape. Field params are optional. Frontend uses code and safe params for translation; unknown codes use the safe fallback message. Older envelopes without params retain their existing detailed messages.
- Field names are DTO/form paths, including nested paths. Object-level errors may have `field: null`. Unknown database constraints produce a general error; no constraint names, SQL, rejected rows or provider messages appear in the response.
- A caller ID is accepted only if it matches `[A-Za-z0-9._:-]{1,120}`. Missing/invalid IDs are replaced with a UUID. `X-Request-Id`, `meta.requestId`, request attributes and MDC agree. The filter runs before security, handles error/async dispatch and restores MDC afterwards. Existing traceId is preserved.
- Error responses use `Cache-Control: no-store`. CORS exposes `X-Request-Id` and `Retry-After`. Expected 4xx faults use INFO; 5xx use ERROR with exception classes and stack locations, with exception messages removed from the logged cause chain.

## HTTP and authentication

| Failure | HTTP / code |
|---|---|
| Invalid query/body types, missing/malformed body | 400 MALFORMED_REQUEST |
| Missing query/header | 400 MISSING_REQUEST_PARAMETER / MISSING_REQUEST_HEADER; REQUIRED field error |
| Input validation | 400 VALIDATION_FAILED; constraint code and safe bounds |
| Return validation / unexpected programming fault | 500 INTERNAL_ERROR |
| Missing/invalid user authentication | 401 UNAUTHORIZED |
| Insufficient permission | 403 FORBIDDEN |
| Tenant faults | Existing tenant code and 403/503 preserved |
| Unknown route/static resource | 404 RESOURCE_NOT_FOUND after security has allowed access |
| Wrong method | 405 METHOD_NOT_ALLOWED; Allow preserved |
| Negotiation / oversized upload / content type | 406 NOT_ACCEPTABLE / 413 PAYLOAD_TOO_LARGE / 415 UNSUPPORTED_MEDIA_TYPE |
| Optimistic locking / stale expectedVersion | 409 VERSION_CONFLICT; errors=[]; params.conflictType=VERSION; params.legacyCode=CONCURRENT_MODIFICATION_CONFLICT |

Both `OptimisticLockException` / `ObjectOptimisticLockingFailureException` and `ExpectedVersionGuard` return this version-conflict contract. During the transition, clients accept both `VERSION_CONFLICT` and the legacy `CONCURRENT_MODIFICATION_CONFLICT` code, preserve the draft, and reload before retrying. This command-level conflict has no field validation errors.

Domain statuses/codes, including 422 and financial idempotency/history conflicts, retain priority. Security entry points and filter faults use the same envelope. A generic IllegalArgumentException is a server defect unless an explicit input validator creates a domain error.

## Pagination, sorting and reports

- One-based list APIs: missing page=1/pageSize=20; integer page <=0 becomes 1; pageSize clamps to 1–100. Invalid direct query types (NaN, fractional, overflow) are 400. Frontend invalid URL/state becomes page=1/pageSize=20 before rendering/requesting. Uncontrolled local tables retain their own paging.
- Payroll reconciliation remains zero-based.
- `orderBy` is mapped through QuerySortPolicy. Unknown fields, whitespace and expressions return 400 INVALID_SORT_FIELD with `field: orderBy`. Each resource has an explicit allowlist; Load `customerId` maps to `customer.id`.
- Monthly reports require year 1–9999 and month 1–12 before date construction: 400 INVALID_REPORT_PERIOD with field errors.

## Lark

| Cause | HTTP / code |
|---|---|
| Known rejected/expired user code or user token | 401 UNAUTHORIZED |
| Rejected system app credentials | 502 LARK_APP_AUTH_FAILED |
| Missing server app configuration | 500 LARK_CONFIG_ERROR |
| Empty, malformed or unknown provider failure | 502 LARK_UPSTREAM_INVALID_RESPONSE |
| Connection failure / upstream 5xx | 503 LARK_UNAVAILABLE |
| Timeout | 504 LARK_TIMEOUT |
| HTTP 429 / provider rate-limit code | 503 LARK_RATE_LIMITED |

Structured integer provider codes are classified in successful envelopes and bounded JSON HTTP error bodies; raw text is never used for classification. Rate limiting includes validated Retry-After (seconds/date, maximum 86400 seconds) and `params.retryAfterSeconds`; invalid headers use `app.lark.rate-limit-retry-after-seconds` (default 30). Frontend safe read retries respect this minimum wait. Provider 5xx faults retain outage semantics. No automatic replay is added to token exchange or write commands.

Credential ownership follows the [official Lark access-token model](https://open.feishu.cn/document/server-docs/authentication-management/access-token/overview).

## PostgreSQL

| SQLSTATE | HTTP / code |
|---|---|
| 23505 / 23503 | 409 DATA_INTEGRITY_VIOLATION; UNIQUE / REFERENCE_CONFLICT field error when mapped |
| 23502 / 23514 / unknown integrity fault | 500 INTERNAL_ERROR |
| 40001 / 40P01 | 503 TRANSACTION_RETRY_REQUIRED; params.retryable=true |
| Class 08 connection failure | 503 DATABASE_UNAVAILABLE |

Known constraint metadata is mapped statically by resource/operation; DELETE does not incorrectly identify a child form field. Payment idempotency retains PAYMENT_IDEMPOTENCY_CONFLICT. Duplicate prechecks identify Truck number, Employee email and Role name. Handlers never read the database or retry a failed transaction. Any write retry must preserve command identity and occur after rollback.

## Verification and rollout

See [implementation status](exception-handling-remediation-status.md) for executed tests, baseline failures and review groups. PostgreSQL fixtures use a disposable database. Browser contracts use real Refine/Table/HTTP adapters with stubbed endpoint responses; they do not prove production Lark credentials or production infrastructure.

Roll out frontend support first. Review groups in order: common handler/request ID, pagination/sort, Lark, database. Roll back from the last applied group when dependencies exist; preserve the error adapter during a backend rollback because it accepts older envelopes.
