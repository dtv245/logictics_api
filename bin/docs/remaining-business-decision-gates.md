# Remaining convention-plan business gates

This register does not reopen RATE-DEC-001…007: all remain CONFIRMED. It records
additional operational contracts needed to implement those decisions without
inventing taxes, signed financial outcomes, driver wages or utilization policy.
Explicit user decisions on 2026-10-04 confirm BILL-DEC-001…006. The locked
contract is [invoice-rating-v1-contract.md](invoice-rating-v1-contract.md).
Earlier options below are historical alternatives, not current blocking decisions.

Final approved backend Convention V1 PLAN COMPLETE: Phases 0–8 and final gates PASS
at V35. [Final verification](backend-plan-final-verification.md) records exact test/
migration/runtime evidence and operational exceptions. No unresolved required decision.

## BILL-DEC-001 — Invoice tax source

- **Decision:** Source of tax assessment for invoices generated from accepted ratings.
- **Status:** CONFIRMED / LOCKED. Audited ACCOUNTING/TRUSTED_EXTERNAL_TAX_PROVIDER
  assessment only; no internal jurisdiction tax engine. BILL-DEC-004 separately
  confirms Accounting's audited REQUIRED/NOT_REQUIRED decision.
- **Options:** Explicit accounting-approved per-line assessment with policy/source/version,
  tax code, exact amount and actor/audit; or a specified jurisdiction tax engine.
- **Existing evidence:** RATE-DEC-005 explicitly leaves TAX to separate policy.
  CreateInvoiceRequest accepts caller tax amounts; there is no qualified versioned
  tax engine/assessment in the rating flow. Payroll tax ports are a different domain.
- **Required business answer:** Received. Assessment audit and mandatory issue gate
  are locked; explicitly zero assessed tax needs evidence, no default zero.
- **Code impact:** Financial generation must validate assessment and reconcile line tax
  and invoice total; until confirmed, no generation with fabricated financial values.
- **DB impact:** Persist exact tax provenance/inputs with consequential invoice basis.
- **Test impact:** Missing/invalid/explicit-zero assessment, line totals, currencies,
  history, retry and concurrent generation on PostgreSQL.

## BILL-DEC-002 — Multi-invoice driver percentage revenue basis

- **Decision:** How new PERCENT_REVENUE calculations consume PRIMARY/SUPPLEMENTAL/CREDIT/REBILL.
- **Status:** CONFIRMED / LOCKED. Versioned DriverPayPolicy selects PRIMARY_INVOICE_REVENUE
  or NET_ELIGIBLE_REVENUE at canonical settlement work date. Post-lock changes append
  ADJUSTMENT/REVERSAL; locked payroll/payslip history stays immutable.
- **Options:** PRIMARY only; net of all eligible documents/reference chain; configurable
  under a versioned driver-pay policy.
- **Existing evidence:** InvoiceRepository.findByLoadId returns Optional<Invoice>;
  DriverPayEngine.java reads it for PERCENT_REVENUE. RevenueCalculator and
  ProfitabilityService use the same one-invoice lookup. Invoice.load is OneToOne;
  V1 ix_invoices_load_id is unique. Simply dropping it would break verified consumers.
- **Required business answer:** Received. Both configurable bases, canonical work-date
  and explicit append-only correction rules are locked; no global basis default.
- **Code impact:** Consistent source resolver/snapshot source IDs, financial consumers
  and percentage-pay eligibility; no architecture-wide refactor or Phase 0–5 re-audit.
- **DB impact:** Forward-only multiplicity/business uniqueness and source identities,
  after semantic choice; preserve applied V1 and old financial history.
- **Test impact:** Multiple invoice sources, tax exclusion, signed credit/rebill effect,
  source claims and immutable prior settlement/payroll regression.

## BILL-DEC-003 — Credit/rebill economic sign and supplemental composition

- **Decision:** Exact financial document representation and amounts for approved flows.
- **Status:** CONFIRMED / LOCKED. Nonnegative face amount with explicit economic sign,
  partial/full credit with caps, full-credit-then-full-replacement rebill, positive
  incremental supplemental without duplicate base freight/FSC/charge events.
- **Options:** Signed negative credit lines or positive credit document with explicit
  economic sign; full credit or supported partial credit; exact replacement/delta basis.
- **Existing evidence:** RATE-DEC-007 permits linked supplemental and credit/rebill, but
  does not specify storage sign/partial credit/composition. There is no credit document
  model. Current accepted rating includes full linehaul/FSC and selected accessorials;
  reusing its full subtotal for a late-accessorial invoice would double bill base freight.
- **Required business answer:** Received. Explicit relationships, line-level credit
  evidence, signed economic amount and approved incremental composition are locked.
- **Code impact:** Approved component selection/claim safety, signed financial aggregation,
  transactional corrections and immutable issued document chains; no silently repeated FSC.
- **DB impact:** Purpose/reference chain, amount/sign/claim constraints and operation-key
  hash idempotency. No automatic classification/backfill of legacy invoices.
- **Test impact:** Full/partial credit as approved, supplemental no duplicate charge,
  correct net revenue, original history, same-key conflict and concurrent retries.

## Phase 7 — Confirmed Optimization V1 contracts

- **Decision:** Eligibility/source/utility/weight/precision/coverage/action contracts.
- **Status:** CONFIRMED/LOCKED; Phase 7 implementation COMPLETE at verified V33.
  All OPT-DEC-001…010 answers received. Canonical:
  [optimization-policy-decisions.md](optimization-policy-decisions.md).
- **Options / required business answer:** Resolved. Exact published status allowlists
  and qualified source registrations are mandatory runtime configuration, not defaults.
- **Existing evidence:** Legacy status/HOS/location/capacity fields do not prove
  qualified future feasibility. New adapters must supply complete audited evidence.
- **Code impact:** Scoped real candidates, full HOS trusted port, hard feasibility
  before exact scoring; transactional existing-Trip assignment, no dispatch.
- **DB impact:** Forward optimization policy/candidate/evidence/acceptance audit
  and resource concurrency guards; never change V1–V29.
- **Test impact:** All gates, source freshness/units, exact curves/weights/HALF_EVEN
  reconciliation/DENSE_RANK, stale/retry/concurrency and tenant isolation.

## Historical fleet placeholder — lifecycle and interval semantics

- **Decision:** Productive eligible, available capacity, excluded time and interval sources.
- **Status:** All Fleet V1 choices CONFIRMED/LOCKED, including exact 008/009 A; Phase 8 COMPLETE. Canonical
  FLEET-DEC-001…009 are now split in
  [fleet-utilization-policy-decisions.md](fleet-utilization-policy-decisions.md).
  This older grouped heading is not a separate confirmed numbering contract.
- **Options:** Approved telemetry/event lifecycle or explicitly sourced status commands
  with deterministic duplicate/timestamp/out-of-order/entry/exit rules.
- **Existing evidence:** Proposed DRIVING/IDLE/LOADING/MAINTENANCE/OFFLINE are not an approved
  mapping. Current truck status cannot prove history; 24h/day is not approved capacity.
- **Required business answer:** Received/locked in canonical FLEET-DEC-001…009:
  exact numerator/denominator/exclusions, bounded validity, strict coverage/numeric
  and audited conflict/correction semantics. No remaining question.
- **Code impact:** Interval reconstruction/report, missing/zero denominator UNAVAILABLE.
- **DB impact:** Minimal historical event schema only when lifecycle is confirmed; no
  history fabricated from current status and no calculation snapshot on dashboard GET.
- **Test impact:** Required utilization/entry/exit/history/open/duplicates/tenant isolation
  cases on PostgreSQL and exact loaded/empty mileage semantics.

## Historical fleet placeholder — health source qualification

- **Decision:** Qualified downtime/PM/cost-mile/breakdown evidence and denominator.
- **Status:** NOT_APPLICABLE to blocking completion: user already authorized UNAVAILABLE
  when a required classification/source is absent. Source qualification is needed only
  to make those individual metrics AVAILABLE later. Canonical downtime/breakdown
  IDs are FLEET-DEC-004/005 in the new register; FLEET-DEC-002 now means productive time.
- **Options:** Supplied maintenance/breakdown classification/event feed; explicit audited
  classification commands; leave unsupported metrics UNAVAILABLE as permitted by plan.
- **Existing evidence:** Existing maintenance tickets are not proven breakdown events;
  explicit Phase 2 loaded/empty miles exist, legacy distance cannot be a denominator.
- **Required business answer:** None to return the explicitly approved UNAVAILABLE outcome.
  Supply classification/source definitions only if AVAILABLE metrics are required.
- **Code impact:** Real-data-only metrics; no every-ticket-as-breakdown or fake zero.
- **DB impact:** Reuse proven events if present; new classifications only with approved meaning.
- **Test impact:** Real downtime/breakdown/mileage examples, missing and zero denominator,
  no cross-tenant aggregation and no dashboard writes.

## Next executable scope

Phase 6 COMPLETE through V29 with verified clean/upgrade/validate and
302 reported / 301 executed / one legacy skip / 94 PostgreSQL methods.
All Optimization V1 business decisions CONFIRMED/LOCKED; Phase 7 COMPLETE.
Qualified adapters, real run/view and atomic existing-Trip acceptance with no dispatch,
stale/retry/resource concurrency and physical tenant gates PASS. Clean V1→V33 and
populated V32→V33 + Flyway validate PASS: 422 reported/421 executed, one legacy skip,
138 PostgreSQL methods; earlier SHA unchanged; diff clean. V34+ was next at that checkpoint.
No Rating/Billing/OPT question is reopened. Phase 8 focused source/policy audit is done;
canonical [FLEET register](fleet-utilization-policy-decisions.md) records all four A
answers CONFIRMED/LOCKED (001/003, 002, 006, 007) and exact 008/009 A replies.
Phase 8 implementation COMPLETE at verified V35; no remaining Fleet V1 business gate or
repeated confirmed direction question. Unsupported health unavailable outcome remains
authorized. Phase 8 clean/V34 upgrade/validate/build/regression/checksum/diff PASS;
final same-code clean/previous-upgrade/API/regression/validate/checksum/diff gates
also PASS. Approved backend PLAN COMPLETE; latest V35, next V36+; production rollout
and source operational configuration/capture are separate, not falsely declared ready.
