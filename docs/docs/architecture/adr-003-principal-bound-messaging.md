# ADR-003: Principal-bound messaging authorization

- **Status:** Implemented and independently verified
- **Date:** 2026-08-15
- **Requirements:** FR-01, FR-06, FR-11, TC-018
- **Delivery task:** SLICE-003

## Context

`/api/messages/**` is role-protected but not principal-bound. The controller accepts employee and sender IDs from clients; detail/message queries do not require membership; create need not include the caller. This permits same-tenant IDOR, impersonation and read-receipt forgery.

SLICE-001 already supplies a tenant-local JWT identity-to-Employee mapping. SLICE-003 must reuse it, preserve current client shapes for one compatibility phase and add participant authorization without changing database schema, global role matchers or unresolved customer/tenant-chat semantics.

## Decision

### Required current employee

Extend the public `CurrentUserService` with `requireCurrentEmployeeId(JwtAuthenticationToken)`. The implementation reuses `getCurrentUser` and returns its mapped Employee ID. A valid identity with no mapping throws `ApiException(ErrorCode.ACCESS_DENIED, ...)`; it never falls back to a request ID.

`MessageController` receives the production `JwtAuthenticationToken` for every endpoint and resolves the current Employee through this method.

### Legacy identity assertions

The three required `employeeId` query parameters and required `SendMessageRequest.senderId` remain unchanged for this compatibility phase.

The controller validates query assertions before service invocation. `MessageService.sendAsParticipant` independently validates the body assertion. A mismatch returns `403 ACCESS_DENIED`; a match is accepted but never used as an alternative principal.

This preserves current clients while making the authenticated mapping authoritative. Removing or making these fields optional is a later API-deprecation slice.

### Conversation boundary

Add these public operations while retaining generic CRUD methods for current internal/seed compatibility:

- `ConversationService.getByIdForParticipant(conversationId, employeeId)`;
- `ConversationService.createForParticipant(request, employeeId)`.

Add `ConversationRepository.findByIdAndParticipant(...)`, which joins the participant relation and returns no row for both a missing conversation and a nonparticipant. The service maps either case to the same `RESOURCE_NOT_FOUND` response.

`createForParticipant` copies the requested participant set, adds the current employee and delegates to the existing transactional creation path. The set and database unique constraint deduplicate membership.

### Message boundary

Add:

- `MessageService.listByConversationForParticipant(conversationId, employeeId, page, pageSize)`;
- `MessageService.sendAsParticipant(employeeId, request)`.

Both require `ConversationParticipantRepository.existsByConversationIdAndEmployeeId`. A nonparticipant receives the same `Conversation not found` 404 as an unknown ID.

`sendAsParticipant` rejects a mismatched legacy `senderId`, reconstructs a trusted request with the current employee ID and delegates to the existing transactional create hook. The persisted sender can therefore never come from an untrusted identity assertion. The generic `create` remains only for existing internal seed usage and is no longer called by the REST controller.

Existing `countUnread(employeeId)` and `markRead(conversationId, employeeId)` already query/mutate by a participant Employee. The controller now supplies only the validated current employee. `markRead` keeps its participant lookup and sequential replay behavior.

### Tenant and role behavior

- `SecurityConfiguration` remains unchanged: recognized tenant roles may enter messaging.
- Participant membership is an additional record-level gate and has no privileged-role bypass.
- All repositories continue to use the routed tenant datasource. No `tenantId` enters a messaging method.
- `isTenantChat` and load-linked conversations remain explicit-participant scoped pending BLK-007.

## Error contract

| Condition | Result |
|---|---|
| Missing/invalid JWT | `401 UNAUTHENTICATED` from the existing security chain |
| JWT has no Employee mapping | `403 ACCESS_DENIED` |
| Legacy actor assertion differs from current employee | `403 ACCESS_DENIED` before data access/mutation |
| Conversation missing or current employee is not a participant | `404 RESOURCE_NOT_FOUND` with the same public message |
| Existing validation failure | Existing `400` envelope |
| Successful create/send | Existing `201`; reads/mark-read remain `200` |

## Rejected alternatives

### Remove actor fields immediately

Rejected for this slice because generated/current clients still send required fields. A later deprecation window can alter OpenAPI/DTO shapes once consumers migrate.

### Ignore mismatched actor fields

Rejected because it hides stale clients and attempted impersonation. Equality validation is compatible for correct clients and fails malicious/stale identity assertions explicitly.

### Enforce membership only in the controller

Rejected because service methods are the application boundary. Participant-scoped service methods keep authorization adjacent to queries/mutations and remain testable without MVC.

### Privileged role bypass

Rejected because no moderation/impersonation audit contract exists. Role authorization does not imply conversation membership.

### Add tenant columns or change global security matchers

Rejected. The implemented tenancy model is database-per-tenant and the current role matrix already permits messaging. This slice fixes record authorization, not tenancy architecture or role policy.

## Test design

- Current-user service: mapped ID is returned; unmapped/missing-email identity fails closed.
- Controller: unauthenticated 401, unmapped 403, mismatched legacy assertion 403 with no downstream access, and current ID forwarding across endpoints.
- Conversation service/repository: creator auto-add/dedup; participant-scoped detail; HQL compilation.
- Message service: participant-scoped list/send, real sender assignment, nonparticipant 404, mismatched sender 403, mark-read replay.
- API functional: employee A creates and sends, B unread/read flow succeeds, C receives 404, impersonation receives 403, and privileged role does not bypass membership.
- Full unit/architecture and Testcontainers verify suites, followed by focused security/code review.

## Frozen implementation boundary

Production files:

1. `modules/identity/service/CurrentUserService.java`
2. `modules/identity/service/impl/CurrentUserServiceImpl.java`
3. `modules/messaging/controller/MessageController.java`
4. `modules/messaging/repository/ConversationRepository.java`
5. `modules/messaging/service/ConversationService.java`
6. `modules/messaging/service/impl/ConversationServiceImpl.java`
7. `modules/messaging/service/MessageService.java`
8. `modules/messaging/service/impl/MessageServiceImpl.java`

Test files:

1. `modules/identity/service/CurrentUserServiceTest.java`
2. new `modules/messaging/controller/MessageControllerTest.java`
3. `modules/messaging/service/ConversationServiceTest.java`
4. `modules/messaging/service/MessageServiceTest.java`
5. `modules/messaging/repository/MessagingRepositoryQueryTest.java`
6. `ApiFunctionalIT.java`

Frozen public method additions:

```java
UUID CurrentUserService.requireCurrentEmployeeId(JwtAuthenticationToken authentication);

ConversationResponse ConversationService.getByIdForParticipant(
    UUID conversationId, UUID employeeId);
ConversationResponse ConversationService.createForParticipant(
    CreateConversationRequest request, UUID employeeId);

PagedResponse<MessageResponse> MessageService.listByConversationForParticipant(
    UUID conversationId, UUID employeeId, int page, int pageSize);
MessageResponse MessageService.sendAsParticipant(
    UUID employeeId, SendMessageRequest request);
```

No other production/test file is authorized without a new impact check and a Tech Lead handback. In particular, `SendMessageRequest`, security configuration, Flyway, DataSeeder, mappers and response DTOs stay unchanged.

## Impact analysis

GitNexus upstream impact on 2026-08-15:

| Existing target | Risk | Evidence |
|---|---|---|
| Seven `MessageController` handlers / controller class | LOW | 0 indexed callers/processes |
| `ConversationService` | LOW | 4 direct / 5 total / 0 processes; controller, implementation, seeder import and test |
| `ConversationServiceImpl` | LOW | 2 direct test references |
| `ConversationRepository` | MEDIUM | 5 direct importers / 0 processes; only a new scoped query is added |
| `MessageService` | LOW | 4 direct / 5 total / 0 processes; controller, implementation, seeder and test |
| `MessageServiceImpl` | LOW | 3 direct test references |
| `ApiFunctionalIT.messaging`, conversation/message service tests | LOW | 0 upstream dependents |
| `CurrentUserService` and implementation | UNKNOWN in HEAD-only index | New untracked SLICE-001 files are absent from the committed graph; exact source/caller review finds one production controller, implementation and two focused test consumers |

No HIGH or CRITICAL code-impact target exists. Implementation, negative tests and the sealed security review mitigate `RSK-006` for the current REST surface; the broader tenant integration gap remains under `RSK-003`.

## Consequences

- The REST attack chain is closed without a schema or route-shape change.
- Existing correct clients continue working; stale/malicious actor assertions fail explicitly.
- Seed data can keep using the generic service create path, while the external controller uses only principal-bound methods.
- Customer chat, implicit tenant-wide membership, load-thread eligibility and removal of legacy actor fields remain explicit follow-up work under BLK-007.

## Implementation outcome

The implementation follows this decision without a DTO, mapper, security configuration, schema or seeder deviation:

- every controller route derives the actor from `CurrentUserService.requireCurrentEmployeeId`;
- legacy actor fields are equality assertions and mismatches fail with `403`;
- conversation detail, message list/send and mark-read are participant-scoped;
- conversation creation force-adds and deduplicates the current employee;
- generic methods remain reachable only from trusted/internal code in current production caller search.

Verification on 2026-08-15:

- focused 21/21, full unit/architecture 191/191 and integration 28/28 PASS;
- Testcontainers A/B/C flow proves same-database impersonation and participant denial, unread `0/1`, mark-read `1→0`, and no privileged-role bypass;
- sealed security diff scan reports 0 findings across 7 surfaces;
- independent review returns `LOW — APPROVE` with no blocking finding;
- `git diff --check` and all configured Maven quality gates PASS.

The review deliberately leaves one evidence limitation: no tenant-enabled two-database messaging E2E combines routing and participant authorization. Tenant validation/routing primitives are tested separately, so this is an owned `RSK-003` release-evidence gap rather than a defect established in this patch.

Sequential mark-read replay creates no duplicate receipt but updates `lastReadAt`. Concurrent receipt creation, future participant removal, generic-method exposure, N+1 mapping and bounded mark-read processing require fresh design/review if their excluded policies become in scope.
