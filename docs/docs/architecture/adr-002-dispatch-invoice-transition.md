# ADR-002: Legacy-compatible dispatch-to-invoice transition

- **Status:** Implemented and verified
- **Date:** 2026-08-15
- **Verified:** 2026-08-15
- **Requirements:** FR-04, FR-08, BR-02
- **Delivery task:** SLICE-002

## Context

A synchronous `LoadDispatchedEvent` listener should issue an existing draft invoice in the same transaction as Load dispatch. It currently queries exact TitleCase `Draft` and writes `Issued`, while executable API fixtures create lowercase `draft`. The silent query miss commits the Load transition without the required invoice transition.

Invoice status remains an unconstrained string across DTO, entity, database and OpenAPI. Current seed data can use legacy TitleCase, and the broader Issued/Sent/payment lifecycle is blocked by unresolved product decisions. The slice therefore needs compatibility without pretending to solve global invoice normalization.

## Decision

### Domain boundary

Add `finance/enums/InvoiceDispatchStatus` containing only the two states owned by this use case: `DRAFT` and `ISSUED`.

The enum:

- exposes lowercase `dbValue()` values;
- matches stored values case-insensitively after trimming;
- does not claim to model Sent, PartiallyPaid, Paid or Cancelled.

Add `Invoice.transitionToIssuedOnLoadDispatch()`, returning `true` only when the current raw status matches Draft. A successful transition writes canonical lowercase `issued`; every other value returns `false` without mutation. The explicit `transition...` prefix avoids JavaBeans treating the boolean method as an `is...` property during MapStruct inspection.

This keeps the state change in the entity as required by the engineering conventions while avoiding a premature full invoice-state enum.

### Repository and event flow

Replace `InvoiceRepository.findByLoadIdAndStatus(loadId, status)` with `findByLoadId(loadId)`. `invoices.load_id` already has a unique index, so one Load maps to at most one invoice and no status index or migration is required.

`LoadDispatchedInvoiceListener`:

1. load the optional invoice by `loadId`;
2. call `transitionToIssuedOnLoadDispatch()`;
3. save and log only when the method returns `true`.

The plain synchronous `@EventListener` remains unchanged. It executes inside `LoadServiceImpl.dispatch`'s transaction and preserves rollback/tenant-thread context behavior.

### Compatibility and canonical output

- Stored `draft`, `Draft` and casing variants are eligible.
- Successful output is always `issued`.
- Missing invoice, unknown status, issued/later status and replay are no-ops.
- The development seeder is not normalized in this slice. Its TitleCase Draft remains an intentional compatibility fixture; global create/update/seeder normalization needs the broader invoice contract decision.

## Rejected alternatives

### Change only the two listener literals

Rejected because lowercase-only lookup would miss current TitleCase seed/historical rows and raw literals would remain in event logic.

### Use `LOWER(i.status)` in JPQL

Viable but keeps transition eligibility in persistence logic and the mutation in the listener. Looking up the one invoice by unique `load_id` and asking the entity to transition is clearer and easier to test.

### Introduce a complete `InvoiceStatus` lifecycle enum

Deferred because Issued versus Sent and broader payment transitions remain unresolved under BLK-004. A partial product decision must not be hidden inside this correctness fix.

### Data migration

Rejected for SLICE-002. The schema is compatible, global historical values are not inventoried and the bounded transition can normalize eligible rows when used.

## Test design

- `InvoiceDispatchTransitionTest`: canonical draft, legacy Draft, non-Draft and replay behavior.
- `LoadDispatchedInvoiceListenerTest`: missing invoice no-op, transition save once and non-Draft no-save behavior.
- `ApiFunctionalIT.invoices`: create a fresh draft Load and lowercase draft Invoice, dispatch via HTTP, then read canonical issued status.
- Existing Load state-machine tests retain invalid repeat-dispatch behavior.
- Run focused tests, full `./mvnw test` and `./mvnw verify`.

## Impact analysis

GitNexus upstream impact on 2026-08-15:

| Existing symbol | Risk | Direct / total / processes | Decision |
|---|---|---|---|
| `LoadDispatchedInvoiceListener.issueDraftInvoice` | LOW | 0 / 0 / 0 | Edit listener orchestration. |
| `InvoiceRepository.findByLoadIdAndStatus` | LOW | 1 direct caller / 1 total / 0 | Replace and update its sole caller. |
| `Invoice` | MEDIUM | 6 direct / 10 total / 0 | Add one bounded method; verify mapper/service consumers with full build. |
| `ApiFunctionalIT.invoices` | LOW | 0 / 0 / 0 | Extend existing real-database API coverage. |
| `DataSeeder.determineInvoiceStatus` | LOW | 1 direct / 3 total / 2 seed processes | Do not edit in this slice. |

No HIGH or CRITICAL impact target is in the selected scope.

## Verification result

- Focused domain/listener suite: 14 tests passed.
- `./mvnw test`: 177 unit and architecture tests passed.
- `./mvnw verify`: the same 177 tests plus 28 PostgreSQL/Flyway/API and Redis integration tests passed; Checkstyle, Spotless, SpotBugs and executable JAR packaging also passed.
- `ApiFunctionalIT`: 21 tests passed. Its isolated HTTP flow creates a lowercase draft invoice, dispatches the linked Load and reads the persisted lowercase `issued` status. The Failsafe report also records the listener execution.
- Security review found no reportable issue. Independent code review returned `LOW — APPROVE`; GitNexus change detection reported LOW risk and no affected indexed process.
- The implementation changes no request/response DTO, OpenAPI schema, Flyway migration or `DataSeeder` behavior.

Rollback is supported by the plain synchronous listener propagating repository failures through `LoadServiceImpl.dispatch`'s `@Transactional` boundary. A persistence-failure fault-injection integration test and concurrent-update test were not added to this narrow casing fix; that explicit residual is tracked as `RSK-005` in project memory.

## Consequences

- The documented dispatch invariant works for canonical and legacy Draft values.
- Transition rules are testable without changing API/schema or broader invoice semantics.
- Generic create/update/search still accept and compare raw case-sensitive statuses; that residual inconsistency remains explicitly outside SLICE-002.
- Sequential replay is verified. Concurrent dispatch or simultaneous generic invoice updates are not claimed safe until the broader lifecycle defines locking/idempotency policy under BLK-004.
