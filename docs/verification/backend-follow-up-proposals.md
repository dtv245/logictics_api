# Audited follow-up proposals, 2026-10-07

Status: APPROVED_FOR_IMPLEMENTATION, 2026-10-07. The user explicitly answered
“Include both fixes” for the Customer default-list query and SETTLED reporting
changes below. This resolves the replacement plan §23 scope decision for these
two findings. Both minimal source fixes are implemented; all seven new PG
regressions pass, along with retained billing/payment/report tests (46 tests in
the targeted run). Clean and populated-clone full runs each passed 563 Java / 214
PG tests, zero failures/errors/skips; 26 Python tests and local executable checks
passed. [Final evidence](backend-remediation-followup-evidence-20261007.json).
Deployment and frontend changes remain outside this task.

## Customer default-list failure

Actual PostgreSQL HTTP evidence in the preceding verification reproduced
`lower(bytea)` when GET `/api/customers` omits `search`. The current repository
uses the same nullable untyped parameter pattern that was corrected for Load
and Trip in BUG-BE-0004. Source inspection confirms both Customer predicates.

Proposed change in `CustomerRepository.search`:

```diff
- OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
- OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))
+ OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
+ OR LOWER(c.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
```

No migration, response or authorization change. Regression must use actual
PostgreSQL HTTP with omitted, empty and nonempty search, pagination and two
physical tenants. Exact graph impact: LOW, two affected symbols through Customer
service/controller. This is a separate availability fix.

## BUG-BE-NEW-004: SETTLED payment reporting

`PaymentService` consumes SETTLED as terminal success and forbids cancellation,
as confirmed in BE-DEC-006. `RevenueCalculator.calculateLoadRevenue` and
`CustomerBalanceCalculator.isSettled` count only COMPLETED, PAID and SUCCEEDED.
For a collectible USD 100 invoice with a USD 40 SETTLED payment, their current
classification reports paid USD 0 and open USD 100, while the reservation
boundary leaves only USD 60 available for a new payment.

Proposed change: include case-insensitive SETTLED in those two successful-payment
classifiers. Pending payments continue to reserve funds without counting as
settled paid revenue. Preserve existing invoice/revenue formulas, currency
checks, void/cancel behavior, economic signs and report response contracts. No
payment status/data reclassification, new settlement command or migration.

Regression must verify actual report outputs, retained success/pending/cancelled
statuses and currency behavior, plus populated pre-V38 SETTLED upgrade fixtures.
The fixture must carry valid invoice financial evidence through the existing
reconciliation path. Exact graph impacts are LOW (three symbols/customer balance
and one direct Load-report controller); domain risk remains HIGH because these
are financial reports. Current working inventory previously contained no
SETTLED rows; that absence does not prove correctness in other tenants.

## Other audited limits

Payment's opaque legacy UUID is generated on creation. Its graph property
impact is UNKNOWN and was confirmed by source search. It is absent from the
Payment response and does not select the routed datasource. No in-repo tenant
authorization consumer was found; external consumers/historical provenance are
unverified. Do not reinterpret or rewrite the field without an authoritative
mapping.

Read-only adjacent-client inspection found `PaymentResponse` and `Payment`
types declaring a required `tenantId`, while the current backend Payment view
omits it. No functional authorization use of that field was found in the
inspected Payment pages. This is contract drift requiring frontend follow-up,
not evidence the field is unused across all consumers. Core key/version fields
remain absent from the inspected client contracts. Its Lark callback already
requests `authentication: "none"`; the server boundary still protects callers
that supply a mismatched token or invoke the service directly.

Shared audit principal columns of length 50 reject longer principal emails.
Resolving that needs a separate schema/identity scope; no schema widening is
included in the proposals above.

The callback identity/query/token tenant mismatch and cached inactive-tenant
authorization found in this review belong to approved BUG-BE-0001/0007. Their
regressions and fixes are proceeding within that scope. The login uses the
server-configured tenant; no client-selected tenant/login redesign is proposed.
