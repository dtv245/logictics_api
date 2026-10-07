# Task 3.3 profitability and versioned cost classification

The continuation specification supplies a code-based default classification policy. `LOGISTICSX_COST_CLASSIFICATION`, version `1`, separates cost category from cost behavior. No historical rows or applied migrations are rewritten. Unknown category/maintenance semantics remain `UNCLASSIFIED`.

## Available routes

- `GET /api/loads/{loadId}/financial-summary?currency=USD`
- `GET /api/reports/profitability/by-load` (optional `loadId` selects one summary)
- `GET /api/reports/profitability/by-lane`
- `GET /api/reports/profitability/by-truck`

All require existing accounting-authorized roles (ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER). They are read-only: no ledger writes, source mutations or calculation snapshots. Full PostgreSQL API tests check real controller mapping, serialization, 403 denial for anonymous/driver, success for accountant and 400 currency mismatch.

## Financial facts

Actual revenue is eligible ISSUED/SENT/PARTIALLY_PAID/PAID invoice subtotal, reconciled against currency-qualified line amounts. DRAFT, CANCELLED, APPROVED, PENDING_APPROVAL, REJECTED and unknown statuses are not revenue. Missing eligible subtotal or mismatched reconciliation rejects rather than being silently dropped.

Quoted load freight is context only, never fallback actual revenue or grouped revenue. Load currency must be explicit. A requested currency must match; no FX conversion or relabeling. Eligible ACTUAL APPROVED/POSTED costs are summed once per canonical row, including permitted signed correction credits. VERIFIED, DRAFT, VOIDED, ACCRUAL and ESTIMATE do not contribute to actual costs. Currency guards apply to participating monetary records; excluded drafts do not contaminate totals. Displayed source rows retain their own currency.

Cost variance needs an authoritative accepted estimate: only ESTIMATE APPROVED/POSTED rows count. A caller-supplied VERIFIED maintenance allocation is not accepted proof. Without an eligible estimate, estimatedCost/costVariance are null and the metric states `NO_AUTHORITATIVE_COST_ESTIMATE`; absence is not zero. With an estimate, difference is computed from raw sums and rounded once at the configured report boundary.

## Classification V1

| Existing category / context | Behavior |
|---|---|
| FUEL, DRIVER, TOLL, ACCESSORIAL, PERMIT | VARIABLE |
| INSURANCE | FIXED_ALLOCATABLE (default V1 category rule) |
| MAINTENANCE + MANUAL/EXPENSE/MAINTENANCE_RECORD + DIRECT | VARIABLE |
| MAINTENANCE + ALLOCATION + CPM_MILEAGE | FIXED_ALLOCATABLE |
| MAINTENANCE without the above evidence, OTHER, missing/unknown category | UNCLASSIFIED |

`DIRECT` is explicit metadata, not inferred from an empty allocation method or an EXPENSE source. The current CostAllocator emits MAINTENANCE/ALLOCATION/CPM_MILEAGE with ESTIMATE basis; this never enters actual profit. There is no allocator proving actual period overhead in the current source; V1 can consume qualifying actual rows when an owning source supplies them. No new enum categories, actual backfills, or unsupported allocation methods are invented.

The policy returns EXCLUDED for known nonactual ESTIMATE/ACCRUAL inputs. The actual-profit calculator first filters to ACTUAL APPROVED/POSTED, so its excludedCost represents only actual rows explicitly excluded by the selected policy; the default V1 has no excluded actual category. Zero excludedCost does not mean estimates or drafts were included.

VariableCost and AllocatedFixedCost sum eligible actual rows with their respective behavior. ContributionMargin = revenue - variableCost; AllocatedProfit = revenue - variableCost - allocatedFixedCost. Both new percentage metrics are ratios (unit RATIO, e.g. 0.25), calculated from raw numerators/revenue, with six decimal places. Existing `marginPercent` and grouped `averageMarginPercent` retain the established PERCENT convention (25.0000) and mean allocated margin. Each currency amount rounds once at the REPORT boundary; raw input amounts remain in the explanation.

Every response includes `costClassification`: policyName, policyVersion, currency, revenue, known variable/fixed/excluded/unclassified sums, both monetary and ratio metrics, and each participating cost's ID, category, source type/ID, basis, allocation method, amount, currency, behavior and reason. This object is serializable for future snapshot use. Current profitability GET architecture persists no results or snapshots; API tests preserve that invariant. Future persistence must store this whole explanation and the policy identity.

Any UNCLASSIFIED row suppresses ContributionMargin/AllocatedProfit and their percentages (`COST_CLASSIFICATION_INCOMPLETE`), even when unknown amounts net to zero. Known component sums remain explainable. An empty eligible cost set is complete and yields revenue as profit; zero revenue suppresses percentages (`ZERO_REVENUE_DENOMINATOR`), while monetary loss remains available. Completeness describes classification of the recorded actual ledger, not accrual completeness or missing company overhead.

## Mileage and grouping

Unit economics use only explicit V3 actual/loaded/empty trip miles. Trips are de-duplicated by ID. Each included trip must reference exactly the requested load through persisted stops. Shared/missing attribution is not prorated; missing/negative mileage is unavailable. Zero denominators return UNAVAILABLE. Loaded/empty miles must reconcile with actual miles when both exist; mismatched partitions suppress loaded/empty metrics and break-even. Legacy load/trip distance never feeds these calculations. All trips must have a complete denominator, not just a subset.

Lane buckets use normalized origin/destination state AND currency. Truck buckets use a unique trip-associated truck AND currency, never current load.assignedTruck or truck.mainDriver. This is attribution from the persisted trip association, not a claim there is a separate immutable truck-assignment history table. Missing/multiple truck associations produce an explicit UNALLOCATED bucket with unavailable attribution metadata; amounts are retained, not silently excluded. Shared-trip mileage still remains unavailable even if a truck can be attributed.

Grouped revenue is actual-only. Group mileage is available only when every load's mileage is available; ratios divide summed numerators by summed denominators. Grouped classification uses raw cost explanations, checks currency and policy identity, sums revenue and recomputes profit and revenue-weighted margins. Any unknown load cost suppresses group profits. Corrupt eligible invoices/currencies fail the report instead of catch-and-skip underreporting. Empty report populations return an empty list.

## Verification

`ProfitabilityFactsTest` covers actual-only/status/currency/reconciliation/credit/estimate rules, complete/missing/shared/inconsistent mileage, currency buckets, trip truck versus reassigned load truck, unallocated retention and error propagation. Live `CostLedgerPostgresTest` exercises all four endpoints against clean V1–V12 PostgreSQL; GET does not create snapshots or costs.

CostClassificationPolicyTest and ProfitabilityCalculatorTest cover every enum category, direct/allocated/ambiguous maintenance, nonactual exclusion, actual exclusion by an explicit policy, credits, zero revenue, zero-net unknown costs, rounding, weighted aggregation, policy mismatch and currency mismatch (including empty cost sets). Live API coverage reconciles maintenance/direct/insurance/credits and suppresses ambiguous maintenance. Full scans and per-load queries remain a later performance-review concern.

Trip-only driver costs are exposed separately in costClassification.unallocatedTripCosts with source/ID/category/trip/classification evidence. They are never implicitly assigned or prorated to every load. Local variable/fixed sums contain load-attributed costs only; any relevant eligible actual trip-only cost suppresses derived contribution/allocated profit and ratios with TRIP_COST_ALLOCATION_REQUIRED. Grouped reports deduplicate this evidence and propagate availability. This dependency correction prevents overstating profit after settlement projection.
