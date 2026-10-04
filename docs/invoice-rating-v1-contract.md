# Rating V1 invoice integration — locked business contract

Authority: explicit user decisions on 2026-10-04. Status: CONFIRMED / LOCKED.
This supplements RATE-DEC-001…007; it does not reopen any rating decision.
6A–6G COMPLETE under approved V1 scope, verified through V29. Final phase-gate
evidence is maintained in plan-progress-summary.md. This is not full-plan completion.

## Decision register

| Decision | Status | Options / approved answer | Existing evidence | Required business answer | Code impact | DB impact | Test impact |
|---|---|---|---|---|---|---|---|
| BILL-DEC-001 Tax source | CONFIRMED | ACCOUNTING or TRUSTED_EXTERNAL_TAX_PROVIDER audited TaxAssessment; internal jurisdiction engine excluded | Current tax fields are caller amounts, not an assessment engine | Received; no new tax rates | Validate assessment identity, currency, amounts and audit; no provider I/O inside invoice transaction | Immutable assessment and invoice reference | Missing, supplied, zero assessed, currency, audit |
| BILL-DEC-002 Driver revenue | CONFIRMED | Versioned policy chooses PRIMARY_INVOICE_REVENUE or NET_ELIGIBLE_REVENUE | Existing policy already stores revenue_basis; settlement has canonical work date | Received | Resolve policy at settlement work date; snapshot document IDs/signs; late changes append adjustments | Preserve locked history and correction references | Both bases, late billing, immutable payroll |
| BILL-DEC-003 Economic documents | CONFIRMED | Positive face amount with PRIMARY/SUPPLEMENTAL/REBILL sign +1, CREDIT sign -1; partial/full credit, full replacement rebill | Legacy load has one invoice; no purpose metadata | Received | Explicit billing chain, line credit caps, incremental supplemental only | Forward multiplicity, business uniqueness, purpose/sign/FKs/claims | Partial/full/over-credit, rebill, supplemental, concurrency |
| BILL-DEC-004 Tax requirement owner | CONFIRMED | Accounting records REQUIRED/NOT_REQUIRED with actor/time/reason/source for load/customer/currency/command | isVatExempt alone does not prove tax requirement | A explicitly selected by user | Require explicit audited decision; never default from customer flag | Persist decision with command outcome | Missing decision, mandatory missing assessment, audited NOT_REQUIRED |
| BILL-DEC-005 Multi-partial-credit full reversal | CONFIRMED | Multiple issued partial credits whose exact subtotal and tax sums fully reverse the original qualify for REBILL; explicit credit evidence IDs required | Partial credit and full economic reversal were locked, aggregate proof representation was unspecified | A explicitly selected by user | Validate complete nonduplicate eligible credit evidence, no over-credit | Immutable rebill credit references | Partial credits summing full, incomplete evidence, duplicates |
| BILL-DEC-006 Pre-lock revenue drift | CONFIRMED | At approve/lock reject SETTLEMENT_REVENUE_BASIS_STALE; explicit recalculation and approval required before lock | Calculation already freezes eligible billing inputs; pre-lock drift cutoff was not defined | A explicitly selected by user | Compare snapshotted basis with current eligible economic inputs at workflow gates; explicit audited recalculation | Append calculation snapshots; preserve finalized history | Drift before approve/lock, recalculation, reapproval, no locked mutation |

## Invoice lifecycle and identity

Generate from an accepted immutable RatingSnapshot, never current RateRule. PRIMARY
identity is physical tenant database + load + customer + currency. Request identity
is tenant + operation + idempotencyKey with normalized input hash. Same key/input
returns existing outcome; different input returns 409 INVOICE_IDEMPOTENCY_CONFLICT.
The two uniqueness guards are independent. External calls occur outside transaction.

PREVIEW remains ephemeral. DRAFT may relink/regenerate only through an explicit
idempotent command. Issued financial history never changes; corrections are new
documents. Tax requirement is explicitly decided by Accounting; REQUIRED without
assessment cannot issue and returns INVOICE_TAX_ASSESSMENT_REQUIRED. NOT_REQUIRED
also requires audit. No assumed zero tax or internal tax-rate calculation.

Assessment captures assessmentId, sourceType/reference, jurisdiction, taxableBasis,
taxAmount, currency, optional supplied policy/reference version, assessedAt/assessedBy.
Any external result requires trusted provenance; a client sourceType label is not
authentication. Accounting-authenticated capture has an independent ingestion audit.

## Corrections and economic revenue

SUPPLEMENTAL contains only approved incremental positive charge events/deltas;
never repeats original linehaul/FSC/already billed charge. Negative changes are CREDIT.
CREDIT requires parent, credited lines and explicit quantity/amount/reason. Per-line
and document cumulative credit cannot exceed creditable amount. Face amounts stay
nonnegative. REBILL is full replacement after full economic credit, linking original,
full credit evidence and a new accepted snapshot. Partial changes do not become rebill.
Full economic reversal may be proved by multiple issued partial credits; the command
must persist their explicit IDs, and subtotal/tax must each exactly reverse the original.
Relationships are explicit FKs, never inferred from customer/date/amount.

Runtime eligible revenue statuses: ISSUED, SENT, PARTIALLY_PAID, PAID
(InvoiceStatus.countsAsRevenue). DRAFT/CANCELLED and other noneligible states have no
economic effect. Revenue uses eligible subtotal (tax excluded) as established in the
existing accounting/settlement contract, multiplied by explicit document economic sign.

PRIMARY_INVOICE_REVENUE includes only PRIMARY; NET_ELIGIBLE_REVENUE uses the eligible
billing chain's signed documents. Driver policy version resolves using the existing
canonical settlement work date, not invoice/payment/current date. After settlement
lock, new billing changes create reviewed ADJUSTMENT/REVERSAL with originalSettlementId,
affectedDocumentId, reasonCode and economicDelta. Locked settlement/payroll/payslip
amounts and prior policy versions never change.

Before approve/lock, BILL-DEC-006 rejects changed economic inputs with
SETTLEMENT_REVENUE_BASIS_STALE; explicit audited recalculation resets approval.
After lock, issue atomically creates a reviewed-later child adjustment or an audited
no-pay-impact outcome. It never modifies original payroll/payslip history. Details:
[settlement-billing-revenue-consistency.md](settlement-billing-revenue-consistency.md).

Legacy invoice purpose is not automatically classified or backfilled. Any adaptation
must preserve historical values and fail closed where original identity cannot be
proved. V1–V29 are immutable; next schema migration is V30+.

## Verification gate

## Implemented 6G.A / 6G.B boundary

V25 captures immutable assessments. V26 adds accepted-snapshot PRIMARY generation,
explicit Accounting tax decisions/allocations, immutable command outcomes, replay and
conflict, DRAFT regenerate with expected snapshot and issued header/line protection.
Missing mandatory assessment rejects generation before any financial record exists;
this version does not create an unresolved-tax invoice and cannot issue one.
Tax rates are not inferred: assessed amounts are authoritative, per-line allocations
are explicitly Accounting-supplied and must reconcile, and tax_rate_percent is nullable.
Audited NOT_REQUIRED permits an actual zero tax amount, unlike absent tax configuration.
Amounts use unrestricted NUMERIC so valid currency minor units are not narrowed to two.
The original unique Load relationship was retained for the historical 6G.B slice;
V27 implements signed consumers/multiplicity together without relabeling legacy rows.

API (ADMIN/ACCOUNTANT): POST /api/invoices/tax-assessments;
GET /api/invoices/tax-assessments/{id}; POST /api/invoices/billing/primary;
GET /api/invoices/billing/{id}; POST /api/invoices/billing/{id}/regenerate;
POST /api/invoices/billing/{id}/issue. No caller actor/tenant override.

## Implemented 6G.C boundary

V27 opens explicit signed billing multiplicity, keeps unclassified legacy rows separate,
adds immutable rebill credit evidence and approved-charge claims. Credits reserve caps
per original line for subtotal/tax/optional quantity; face values remain positive.
Multiple eligible issued credits may prove exact full reversal before full replacement.
Supplemental selects only positive approved ACCESSORIAL events, never base freight/FSC.
Issued lines cannot move into another DRAFT. Revenue/profit/customer balance and driver
calculators use document economic sign, not negative face amounts or invoice ordering.
APIs add POST /api/invoices/billing/{id}/supplemental, /credit and /rebill.
Clean V1→V27 regression PASS: 292 reported/291 executed/one legacy skip, 84 PG methods.
Populated V26→V27 verification also PASS (codex_regression_20261004120507136354,
/tmp/logisticsx-regression-mdlydb65). Driver correction/stale gates remain 6G.D.

## Implemented 6G.D / final verification

V28 audited pre-lock recalculation/post-lock adjustments and V29 immutable driver
snapshot history. NET/PRIMARY-only, work-date policy version, stale approve/lock,
reapproval, credit issue versus lock, rounding accumulation, retry/input drift,
locked payroll/payslip and physical PostgreSQL tenant isolation are verified.
Current full suite: 302 reported / 301 executed / zero failures/errors / one named
legacy skip, 94 PG methods. Final-source clean V1→V29, populated V27→V29 batch and
previous-latest V28→V29 migration/regression/validate PASS. No historical schema
rewrite or added skip. See progress for final exact database/log evidence.
