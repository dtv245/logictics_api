# SLICE-003: Principal-bound messaging authorization

- **Status:** VERIFIED — accepted by Product Owner for the bounded REST actor/participant outcome
- **Date:** 2026-08-15
- **Requirements:** FR-01, FR-06, FR-11, US-14, UC-11, API-MSG-01, TC-018
- **Dependency:** VERIFIED `GET /api/me` and current employee mapping from SLICE-001

## 1. Business outcome

An authenticated tenant employee may act only as themselves and may read or mutate only conversations in which they are an explicit participant. A role that permits messaging is necessary but never bypasses record-level participant authorization.

This slice closes same-tenant conversation IDOR, sender impersonation and forged read receipts without redesigning customer chat, tenant-wide membership or realtime delivery.

## 2. Original security defect

The endpoint role gate allows all recognized tenant roles to use `/api/messages/**`, but the current REST handlers trust actor IDs supplied by the client:

- conversation list and unread count query by arbitrary `employeeId`;
- message creation accepts arbitrary `senderId` and checks only that the claimed sender is a participant;
- mark-read accepts arbitrary participant `employeeId`;
- conversation detail and message list have no participant check;
- conversation creation does not require the authenticated caller to be a participant.

A valid tenant user who learns another employee or conversation UUID can therefore enumerate conversation metadata, read content, send as another participant or alter that participant's read state.

## 3. Authoritative rules

- FR-01 requires authentication, tenant resolution and authorization before returning a protected payload.
- FR-11 requires participant authorization for opening, sending and reading conversations.
- FR-11 AC2 and TC-018 require a nonparticipant read/send attempt to return `403` or `404`.
- ADR-001 establishes the server-side JWT email to tenant employee mapping; a client-provided employee ID cannot establish caller identity.
- The current Spring authorization matrix still controls which roles may enter messaging. SLICE-003 adds participant authorization and does not change the role matrix.

## 4. Actor and tenant policy

1. The actor is the tenant-local Employee resolved from the validated JWT by the current-user boundary.
2. A valid identity with no Employee mapping fails messaging closed with `403 ACCESS_DENIED`; the API never falls back to a supplied ID.
3. SuperAdmin, Owner and other privileged roles receive no implicit conversation bypass. Moderation/impersonation would require a separate audited contract.
4. Conversation existence and content are hidden from nonparticipants with `404 RESOURCE_NOT_FOUND`, using the same response as a nonexistent conversation.
5. Every lookup runs through the current tenant datasource. Database-per-tenant must be enabled in multi-tenant deployments; disabled tenancy is supported only for a single tenant/database runtime.
6. `isTenantChat=true` remains explicit-participant scoped. Automatic tenant membership is not inferred from the flag in this slice.

## 5. Backward-compatible identity contract

The current query/body shapes remain required for this hardening slice so existing generated clients do not break:

- `employeeId` remains on conversation-list, unread-count and mark-read requests;
- `senderId` remains in `SendMessageRequest`;
- response DTOs, routes, pagination and success statuses remain unchanged.

Those legacy actor fields are compatibility assertions, not identity selectors:

- the server first resolves the current employee from the JWT;
- an asserted ID equal to that employee is accepted;
- an asserted ID belonging to anyone else returns `403 ACCESS_DENIED` before conversation/message access or mutation;
- a later contract-cleanup slice may make the fields optional/deprecated and then remove them after client migration.

## 6. Endpoint behavior

| Endpoint | Required behavior |
|---|---|
| `GET /api/messages/conversations` | Validate asserted `employeeId` equals current employee, then list only that employee's conversations. |
| `GET /api/messages/conversations/{id}` | Return detail only when current employee is an explicit participant; otherwise `404`. |
| `POST /api/messages/conversations` | Preserve requested invitees, add current employee exactly once and resolve every participant inside the current tenant. |
| `GET /api/messages` | Require current employee membership before returning any message content. |
| `POST /api/messages` | Validate asserted `senderId` equals current employee, require membership, and persist the current employee as sender. |
| `GET /api/messages/unread-count` | Validate asserted `employeeId` and calculate only the current employee's unread count. |
| `POST /api/messages/conversations/{id}/read` | Validate asserted `employeeId`, require membership, create receipts/update `lastReadAt` only for the current employee. |

## 7. Acceptance criteria

1. Missing/invalid JWT returns `401` and does not invoke messaging services.
2. A valid JWT that has no tenant Employee mapping returns `403` and performs no conversation/message mutation.
3. An asserted legacy `employeeId` or `senderId` different from the current employee returns `403`; no content, receipt, timestamp or message is written.
4. Conversation list and unread count always use the current employee after equality validation.
5. Conversation detail and message list return the normal `404 RESOURCE_NOT_FOUND` envelope for both nonexistent and nonparticipant conversation IDs.
6. Conversation creation adds and deduplicates the current employee even when `participantIds` omits them; all requested participants must exist in the current tenant.
7. Send persists the current employee as sender and preserves content validation. A current employee who is not a participant receives `404`, and `lastMessageAt` is unchanged.
8. A message sent by participant A increases participant B's unread count but not A's own count.
9. Mark-read creates receipts and updates `lastReadAt` only for the current employee. Repeating mark-read returns zero and creates no duplicate receipt.
10. Owner/SuperAdmin without membership receives the same participant-scoped `404`; role privilege does not bypass the record gate.
11. Cross-tenant or unknown employee/conversation/load IDs resolve only inside the routed tenant and return a non-disclosing error.
12. Existing routes, `200`/`201` success statuses, pagination/order, response DTOs and ApiResponse envelope remain unchanged.

## 8. Verification scope

- Current-user service tests for required Employee mapping and fail-closed behavior.
- Controller tests for `401`, unmapped `403`, correct legacy assertions and mismatched actor `403` without downstream service calls.
- Conversation service tests for auto-add/dedup and participant-scoped detail.
- Message service tests for participant-scoped list/send, actual sender assignment, receipt creation and no mutation before a denied write sink.
- One-database PostgreSQL/API functional flow with employees A/B/C: A creates/sends, B reads and replays mark-read, C receives `404`, and impersonation receives `403`.
- Full `./mvnw test`, `./mvnw verify`, focused security review and independent code review.

## 9. Explicit exclusions and unresolved product decisions

- Customer identity/chat: current participants reference Employee only, while customer identities may have no Employee mapping.
- Implicit membership or broadcast semantics for `isTenantChat=true`.
- Eligibility for creating load-specific threads beyond the current tenant/load existence rule.
- Participant discovery, join/leave, removal, moderation or audited administrative access.
- SignalR/WebSocket, typing indicators, push notification, message edit/delete/search, retention/encryption and attachment handling.
- Offline queue/idempotency and concurrent mark-read conflict policy.
- Removing legacy actor fields from OpenAPI/DTOs before a client deprecation window is approved.

## 10. Implementation and verification result

The bounded implementation matches the seven endpoint rules without changing request/response DTOs, routes, success statuses, pagination, the shared envelope, security matchers, Flyway or DataSeeder.

| Evidence gate | Result |
|---|---|
| Exact source scope | 8 production and 6 test files from ADR-003; no unapproved runtime file |
| Focused tests | 21/21 PASS: CurrentUserService 5, MessageController 7, ConversationService 4, MessageService 4, HQL translation 1 |
| Full regression | 191/191 unit/architecture PASS |
| Integration | 28/28 PASS; A/B/C messaging flow runs against one Testcontainers PostgreSQL database |
| Security | Sealed diff scan PASS with 0 findings and 7 reviewed surfaces |
| Independent code review | `LOW — APPROVE`; no blocking correctness, compatibility or maintainability finding |
| TC-018 | PASS: nonparticipant SuperAdmin cannot read or send and receives `404` |

Acceptance criteria 1–10 and 12 are fully evidenced. AC11 is partially evidenced: JWT tenant validation, filter cleanup and no-fallback datasource routing have passing unit tests, but messaging has no tenant-enabled, two-database end-to-end scenario. This remains project risk `RSK-003`; the slice must not be described as complete cross-tenant E2E proof.

Repeated mark-read returns zero and creates no duplicate receipt, but it still updates the participant's `lastReadAt`. The frozen AC9 promises receipt idempotency, not a strict no-write replay.

The HQL test builds Hibernate metadata with the PostgreSQL dialect and translates repository queries without a live database connection. Actual PostgreSQL persistence evidence comes from `ApiFunctionalIT`, not from the HQL translation test.

Non-blocking follow-ups are the public generic service surface for trusted internal callers, concurrent mark-read/membership-change behavior, N+1 mapping and unbounded unread receipt processing. These do not reopen the fixed REST authorization path.
