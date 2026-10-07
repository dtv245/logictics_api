# Billing / driver revenue consistency — V1

Authority: BILL-DEC-002/003/006, CONFIRMED/LOCKED. No new compensation basis,
earning date, ratio, tax or financial rounding default is introduced.

## Before finalization

SettlementRevenueGuard compares frozen percentage inputs with current eligible
economic sources when approving or locking ORIGINAL settlement. Monetary inputs,
document IDs, purposes and signs must agree. A non-economic ISSUED→PAID status
change alone does not make the basis stale. PRIMARY-only ignores other documents;
NET uses the eligible explicit billing chain. Unknown legacy identity fails closed.
Drift returns 409 SETTLEMENT_REVENUE_BASIS_STALE, without changing approved amounts.

POST /api/driver-settlements/{id}/recalculate-revenue requires idempotencyKey,
expectedSnapshotId, reasonCode/reason and an authenticated tenant employee.
It recalculates evidenced percentage lines under the original earning-date policy,
appends a new calculation snapshot, preserves the prior snapshot and resets review
and approval to CALCULATED. Accounting must submit/review/approve again. Finalized
settlements cannot use this command. Other earning/reimbursement/deduction inputs
are not silently recalculated by a billing-specific correction.

## After finalization

Issue atomically persists the billing outcome and any affected finalized ORIGINAL
settlement's append-only correction. All effects are database-owned in the same
short transaction; no external tax/provider/payment call and no new messaging
framework. No established transactional outbox exists for these local-only effects.

The original percentage snapshot determines policy ID/version, explicit revenue
basis, ratio, rounding version and canonical earning date. Invoice/payment/current
date never selects a replacement driver policy. A new supplemental/credit/rebill
affects NET only; PRIMARY-only never implicitly adopts the chain. An already
included document is not counted twice. Currency/customer/chain attribution must
prove the original source. Unknown or unavailable frozen source rejects the command.

For each original percentage line:

    resultingRevenue = frozenRevenue + sum(previousEligibleEconomicDeltas) + newDelta
    targetPay = originalAllocationRounding(resultingRevenue × frozenRatio)
    newPayDelta = targetPay - frozenPay - sum(previousPayDeltas)

This avoids rounding every incremental economic delta independently. A legitimate
zero pay delta creates an immutable NO_PAY_ADJUSTMENT_REQUIRED audit, not a zero
settlement/payment. Nonzero delta creates a CALCULATED ADJUSTMENT for normal review,
approval and lock. Negative driver cost correction uses the existing DEDUCTION /
DRIVER_COST_CORRECTION convention, not an invented negative earning face amount.
Locked settlement, payroll, payslip and policy history remain unchanged.

POST /api/driver-settlements/{id}/billing-adjustments is the explicit retry/recovery
command. It requires idempotencyKey, affectedDocumentId, reasonCode/reason and actor
from authentication. Operation/key/hash replay safety and originalSettlement/document
business-source uniqueness are independent protections. Input drift returns 409
SETTLEMENT_REVENUE_IDEMPOTENCY_CONFLICT. Alias retry keys are bound even if automatic
issue has already applied the source.

## Storage / transaction / observation

V28 adds immutable settlement_revenue_commands and settlement_billing_adjustments.
Audit includes original/affected/child IDs, policy/version, earning date, eligible
economic delta, pay delta, prior/resulting revenue, reason/code, actor and timestamp.
Document face/sign remains independently auditable via the immutable invoice FK.
V29 makes DRIVER_SETTLEMENT calculation history immutable even after supersession.
No existing financial amount, date or policy is backfilled or rewritten.

Load-source locks are acquired in sorted order before invoice/settlement row locks.
Issue locks the union of affected settlement Load sources; approve/lock/recalculate/
adjustment use the same source ordering. A concurrent issue either precedes lock
and makes the old basis stale, or follows lock and creates an audited child.

Committed driver commands log operation, policy version, command correlation ID,
duration and success through existing SLF4J. API domain failures record error code
and sanitized request correlation without raw financial payloads or secrets.

## Verification

Required cases cover stale approve/lock, explicit recalculate/reapproval/history,
NET and PRIMARY-only, later policy versions vs earning date, immutable locked payroll/
payslip, small-delta aggregate rounding, replay/input drift and issue-vs-lock race.
Physical cross-tenant repository isolation is tested with two real PostgreSQL
databases, alongside existing authentication/actor guards. V28/V29 are applied and
immutable; final current-source clean and populated upgrade evidence belongs in
plan-progress-summary.md. This document alone does not claim a phase gate has passed.
