# SLICE-002: Load dispatch issues an existing draft invoice

- **Status:** VERIFIED — Product Owner accepted on 2026-08-15
- **Date:** 2026-08-15
- **Requirements:** FR-04, FR-08, BR-02, US-05, US-10, UC-04, UC-08, API-LOAD-05
- **Excluded blockers:** BLK-004 public payment, tax, refund, currency and Stripe semantics

## 1. Business outcome

When an eligible Load is dispatched, its existing draft invoice becomes issued in the same unit of work. This closes a deterministic implementation defect without designing the broader invoice/payment lifecycle.

The slice does not create a missing invoice. Automatic invoice creation is described inconsistently across legacy documents and is not implemented by the current Spring event flow.

## 2. Authoritative rule

The source hierarchy in `feature-delivery-plan.md` gives precedence to the following evidence:

- `project-specification.vi.md` FR-04 step 5 and AC5 require dispatch to update Load status/timestamp, invoice and event consistently.
- `business-spec.md` section 2.1 states: when a Load changes to Dispatched, an Invoice in Draft changes to Issued.
- `project-specification.vi.md` FR-08 AC1 confirms Issued as the result of issuing an invoice.
- The executable fixtures and Postman flow for this transition use lowercase lifecycle tokens; generic invoice API/database fields still accept raw strings. Business-document TitleCase names are conceptual labels, and this slice canonicalizes only a successful dispatch transition.
- `engineering-conventions.md` requires explicit status conversion and forbids raw status literals in service logic.

## 3. Original behavior and defect

`LoadServiceImpl.dispatch` transitions and saves the Load, then publishes `LoadDispatchedEvent` inside the transaction. `LoadDispatchedInvoiceListener` runs synchronously so a listener failure rolls the dispatch back.

The listener searched for exact status `"Draft"` and wrote `"Issued"`. API fixtures create invoices with `"draft"`, so no row was found and the documented transition did not occur. The development seeder also emits legacy TitleCase invoice values, proving that compatibility with already-created mixed-case data had to be handled explicitly.

The implemented listener now loads the unique invoice by `loadId`, asks the Invoice entity to perform the bounded transition and saves only when the entity changed. Canonical and legacy Draft representations are recognized; output is always lowercase `issued`.

## 4. Frozen behavior

1. The trigger is a successful `POST /api/loads/{id}/dispatch` transition from canonical `draft` to `dispatched`.
2. A linked invoice whose status represents Draft is eligible; canonical persisted output after the transition is `issued`.
3. A Load with no linked invoice still dispatches successfully; SLICE-002 does not create an invoice.
4. A linked invoice in any non-Draft state remains unchanged and is not saved by the listener.
5. Replaying `LoadDispatchedEvent` after a successful issue is a no-op; the transition never lowers or rewrites a later status.
6. Load and invoice mutations remain in one transaction. Failure while issuing the invoice must fail and roll back dispatch rather than commit a partial state.
7. Calling the dispatch API again for a non-draft Load preserves the current `400 INVALID_STATE_TRANSITION` contract; HTTP-status normalization is a separate decision.
8. The transition changes only invoice status. Totals, tax, due date, line items, sent timestamps/email and payment state remain untouched.

## 5. Acceptance criteria

1. Given a `draft` Load and linked `draft` invoice, when dispatch succeeds, then the Load is `dispatched`, `dispatchedAt` is set and the invoice is `issued`.
2. Given a compatible legacy Draft representation already stored for the linked invoice, when dispatch succeeds, then the invoice is normalized to canonical `issued` without a schema migration.
3. Given a draft Load with no invoice, when dispatch succeeds, then no invoice is created and no finance error occurs.
4. Given a linked invoice at `issued`, `partially_paid`, `paid`, `cancelled` or an unknown non-Draft value, when the event runs, then its status and audit state remain unchanged and no save occurs.
5. Given the same event runs after the first successful issue, then it is a no-op and does not save the invoice again.
6. Given invoice persistence fails during synchronous event handling, then the dispatch transaction fails; integration evidence must show no partially committed Load transition when practical.
7. Focused tests use lowercase `draft`/`issued` for the canonical contract and include a legacy-case compatibility case.
8. No production logic in the changed path compares or assigns `"Draft"` or `"Issued"` raw literals.

## 6. Contradictions and decisions deferred

- `features.md` describes generating invoices for completed loads, while `invoices.md` describes creation with a Load and `business-spec.md` describes issuing an existing draft at dispatch. SLICE-002 implements only the last rule.
- `business-spec.md` mentions a Sent status, while `invoices.md` treats Issued as awaiting payment and send as timestamp/email metadata. Sent is outside this slice.
- Invalid transition documentation varies between HTTP 400, 409 and 422. The existing Spring `400 INVALID_STATE_TRANSITION` behavior is preserved.
- Full invoice input validation and normalization remain incomplete. This slice must not silently turn into a public invoice lifecycle redesign.

## 7. Verification scope

- Focused domain/listener tests: canonical draft issue, legacy Draft compatibility, non-Draft no-op, missing invoice and replay no-op.
- Existing Load state-machine tests: only draft Loads dispatch; repeat/terminal transitions fail.
- API/integration evidence: create a draft Load and draft invoice, dispatch through the endpoint, then read the invoice as issued.
- Full `./mvnw test` and `./mvnw verify`, followed by security/code review proportional to the changed symbols.

## 8. Verification result

| Acceptance criterion | Evidence | Result |
|---|---|---|
| AC1, AC2, AC4, AC5, AC7, AC8 | `InvoiceDispatchTransitionTest` and `LoadDispatchedInvoiceListenerTest` | PASS — 14 focused tests |
| AC1 HTTP/event/database path | `ApiFunctionalIT.invoices` creates a local Load/Invoice, dispatches and reads `issued` | PASS — `ApiFunctionalIT` 21/21 |
| Full regression and integration | `./mvnw test`; `./mvnw verify` | PASS — 177 unit/architecture plus 28 integration tests |
| AC3 no invoice | Listener unit test proves missing invoice is a no-op; existing dispatch flow remains unchanged | PASS |
| AC6 atomic rollback on persistence failure | Synchronous listener plus outer `@Transactional` and uncaught repository exception | STRUCTURAL EVIDENCE — fault injection not run; tracked as RSK-005 |
| Security and change review | Exact seven-file supporting-chain review; GitNexus `detect_changes` | PASS — 0 security findings; code review `LOW — APPROVE`; 0 affected indexed processes |

The slice is not evidence that generic invoice status input, concurrent dispatch, payment reconciliation or the complete FR-08 lifecycle is production-ready.

## 9. Out of scope

- Invoice auto-creation, line-item calculation, tax/rounding and due-date policy.
- Send/email behavior, payment links, public payment, partial/full reconciliation and refunds.
- Stripe intents, Connect, webhook signature/idempotency and provider failure handling.
- Global migration or cleanup of all historical invoice status values.
