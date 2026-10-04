# Phase 6 — Rating policy decision gate

Decision gate: CONFIRMED / LOCKED for Rating V1. Phase 6 COMPLETE: 6A–6G verified through V29 with final clean/upgrade/regression gates. User confirmed V1: FLAT/PER_MILE linehaul and INDEX_BASED_MPG FSC. Other methods are deferred and invocation returns UNSUPPORTED_RATE_METHOD, never falls back. Additional BILL-DEC-001…006 are CONFIRMED in docs/invoice-rating-v1-contract.md. Full plan remains incomplete pending Phases 7–8.

Source order: implementation plan → progress → decision/domain docs → ADRs → Java → migrations → controllers → tests. LOCKED user decisions override conflicting older text; verified runtime is current V29, not the historical V20 evidence below. Alternatives are not implemented policy. All applied V1–V29 stay immutable; forward changes start V30+.

## Verified baseline and confirmed constraints

- Phase 0–5 are COMPLETE. V1–V20 are immutable; a defect requires a forward V21+ migration. Clean V1→V20 and populated V19→V20 upgrade were verified previously. The seven `LEGACY_PAID_RUN` backfills are historical facts and are not to be rerun on the source database.
- PayrollRun terminal state is `COMPLETED`; every item must be `PAID` or `NO_PAYMENT_REQUIRED`. The latter records actor/time/reason code/context, creates no zero-value payment, and preserves its zero-net settlement as `LOCKED`.
- Customer rating must resolve deterministically. Equal-precedence matches must fail with `RATE_RULE_AMBIGUOUS`; database order cannot break the tie.
- Missing authoritative input must remain a validation/unavailability outcome, not a rate of zero or an inferred value. No default precedence, mileage basis, DOE source, MPG, rounding mode, weights or financial examples may enter production code.
- Monetary calculations use `BigDecimal` and explicit currency. Once consequential rating is accepted/persisted, later policy changes must not rewrite its historical financial basis. The exact acceptance boundary remains RATE-DEC-006.
- Database-per-tenant and Flyway schema ownership are retained from ADR-001/002. GET previews/reports do not write snapshots. Existing error/authorization/observability conventions will be reused when a real operation is implemented.

## Decision register

| Decision | Status | Implementation gate |
|---|---|---|
| RATE-DEC-001 Rate precedence | CONFIRMED | Smaller explicit priority wins; equal winning priority ambiguous; requested pickup LocalDate; inclusive dating and exact/wildcard dimensions |
| RATE-DEC-002 Eligible mileage basis | CONFIRMED | Per-contract/component basis, canonical MILE, explicit load attribution/provenance and fail-closed missing sources |
| RATE-DEC-003 Fuel index source | CONFIRMED | EIA weekly on-highway ULSD, explicit region, latest nonfuture observation, explicit staleness, immutable accepted input |
| RATE-DEC-004 Contract MPG | CONFIRMED | Versioned Rate/FSC policy MPG/base price; positivity/units; no fallback |
| RATE-DEC-005 Rounding | CONFIRMED | RatingPolicyV1 DECIMAL128, FSC unit rate scale 6 HALF_UP, currency boundary rounding, unrounded min/max |
| RATE-DEC-006 Invoice rating snapshot | CONFIRMED | Ephemeral preview → immutable accepted snapshot → invoice reference; append-only corrections |
| RATE-DEC-007 Invoice idempotency | CONFIRMED | One PRIMARY per tenant/load/customer/currency plus operation-scoped input-hashed retry key; supplemental/credit/rebill links |

`BLOCKED` means the complete business answer is absent. A confirmed technical invariant or a legacy field is not sufficient to label the whole decision `CONFIRMED`. `NOT_APPLICABLE` requires an explicit business scope exclusion; none is currently approved. Confirmation must record the exact answer, business owner, evidence/date and affected policy version. Partial answers remain BLOCKED with the unresolved parts identified.

All seven decisions were explicitly confirmed by the user on 2026-10-04 with the rules below. No whole-decision NOT_APPLICABLE exclusions were chosen; MINIMUM_CHARGE mileage is explicitly NOT_APPLICABLE. Earlier partial replies are superseded by the complete answers. Existing-evidence paragraphs describe the inspected V20 baseline before new Phase 6 implementation, not proof of new-feature completion.

## RATE-DEC-001 — Rate precedence

**Decision:** Ordered matching/precedence, effective business date/timezone and rule version ownership.

**Status:** CONFIRMED — user/business owner, 2026-10-04.

**Options:** A. Customer-specific lane → customer default → company default. **B selected by the user:** Explicit priority on matching rule versions; smaller number wins; equal priority is ambiguous. C. Contract-owned ordered dimensions such as customer, lane, equipment, service and tier. A/C are unselected alternatives. No dimension-specific implicit tie breaker is authorized.

**Existing evidence:** No production RateRule/CustomerContract resolver, entity or table exists in the inspected Java/V1–V20 source. The plan's proposed `rate_rules` DDL has customer/effective/version fields but no authoritative matching hierarchy. It is a draft, not an applied migration or approved policy. Customer identity and load requested pickup/delivery dates exist. Their presence does not select the effective rating date. `DriverPayPolicyResolver` is payroll-specific and does not establish customer rate precedence.

**Required business answer — received:** Smaller numeric priority wins. If more than one candidate has that winning priority, fail `RATE_RULE_AMBIGUOUS`. Supported Rating V1 dimensions are customer, contract, lane, equipment, service and tier. Null rule dimension is wildcard; non-null must exactly match request/load, and all configured dimensions must match. Filter candidates first, then choose the minimum priority; do not count specificity or apply any implicit tie breaker. `pricingDate = Load.requestedPickupDate` as business LocalDate. Effective intervals are inclusive: effectiveFrom <= pricingDate and (effectiveTo is null or pricingDate <= effectiveTo). Resolver applies no timezone conversion. Missing requested pickup date fails `RATING_PRICING_DATE_REQUIRED`, never current/invoice/creation/dispatch date. Source adapter evidence: V20 Load stores requestedPickupDate as OffsetDateTime/TIMESTAMPTZ. PostgreSQL does not preserve the original offset, so extracting the persisted UTC date cannot prove historical business LocalDate. User subsequently CONFIRMED an independent pickup business DATE: capture explicit LocalDate at command boundary, preserve legacy instant separately, never derive from persisted TIMESTAMPTZ and never backfill history. V22 implements requestedPickupBusinessDate/requested_pickup_business_date with its own actor/time/reason/provenance correction chain; omitted date leaves existing promise unchanged. Rating reads this DATE only; historical NULL rejects RATING_PRICING_DATE_REQUIRED. Accepted snapshots must preserve exact pricingDate plus pricingDateSource LOAD_REQUESTED_PICKUP_DATE.

**Code impact:** Resolver input/context, deterministic comparator, effectivity/version selection, owner authorization; actual failures `RATE_RULE_NOT_FOUND` and `RATE_RULE_AMBIGUOUS` follow the existing error architecture when implemented.

**DB impact:** V21+ versioned rate/contract schema, confirmed dimension columns/relations, effective intervals and supported overlap constraints; a used version is immutable. Indexes follow the approved matching query.

**Test impact:** One match, more-specific and approved fallback match, no match, same-precedence ambiguity, expired/future rules, exact date boundaries/timezone, currency mismatch, unchanged historical used version. PostgreSQL tests for version/overlap guards.

## RATE-DEC-002 — Eligible mileage basis

**Decision:** Source/unit/attribution selected independently for linehaul, FSC, minimum charge and rate tiers.

**Status:** CONFIRMED — user/business owner, 2026-10-04.

**Options:** A. Actual loaded miles. B. Actual all operational miles. C. Planned loaded miles. D. Approved contract/practical route miles. **E selected by the user:** Per-contract and per-component explicit basis. The received answer below defines the approved configurable bases; none is a universal fallback.

**Existing evidence:** V3 and `Trip` expose `planned_distance_miles`, `actual_distance_miles`, `loaded_miles`, `empty_miles` as `BigDecimal`/`NUMERIC(12,3)`. They are trip-level, not load-level customer rating allocations. The model does not contain separate planned-loaded and actual-loaded fields or an approved contract/practical mileage source. `Load.distance` and `Trip.totalDistance` remain legacy inputs with unresolved units/semantics for this purpose. Existing mileage/pay/ratio tests prove explicit fields are used without legacy fallback; they do not approve a billing basis.

**Required business answer — received:** Configure LINEHAUL, FSC and RATE_TIER independently per contract/rate component. MINIMUM_CHARGE is a monetary floor and has mileageBasis NOT_APPLICABLE. Rating V1 bases: CONTRACT_MILES, PLANNED_LOAD_MILES, ACTUAL_LOADED_MILES, ACTUAL_ALL_MILES. Canonical unit MILE; persist decimal domain precision and do not use floating-point arithmetic. CONTRACT_MILES comes directly from the Load's contract/rate agreement, is non-null/nonnegative, has supported unit and effective contract/customer/currency/rule context, and records sourceType CONTRACT, reference/contractId, sourceVersion, originalValue/unit, normalizedMiles, entered/importedBy and capturedAt. PLANNED_LOAD_MILES requires an authoritative Load-level route; if absent, return RATE_MILEAGE_UNAVAILABLE. ACTUAL_LOADED_MILES requires completed execution and actual loaded movement attributable to that Load; ACTUAL_ALL_MILES requires approved Load-attributed actual miles including only contract-eligible empty miles. No planned↔actual fallback. Multi-load Trip requires explicit load route/contract/movement-leg attribution; absent attribution fails RATE_MILEAGE_ATTRIBUTION_REQUIRED. Never divide equally/by load count/by revenue/by weight. Single-load Trip may supply mileage only when its selected basis and field semantic are proven. Snapshot each component's type, basis, source type/reference/version, original value/unit and eligible miles. The example component mapping supplied by the user is illustrative, not a global default.

**Code impact:** Explicit basis resolution with per-component provenance; no silent actual↔planned↔loaded fallback. Missing approved basis produces `RATE_MILEAGE_UNAVAILABLE`/`RATING_VALIDATION_REQUIRED` as applicable. No conversion or allocation formula is selected yet.

**DB impact:** New mileage provenance/load-allocation/contract-route storage only if the approved basis requires it; V3 columns stay intact. Persist accepted values/source identities with the rating snapshot under RATE-DEC-006.

**Test impact:** Available/missing basis, actual/planned separation, zero, negative, multi-load attribution, precision and reproducible accepted mileage. PostgreSQL integration if new mileage/provenance records are persisted.

## RATE-DEC-003 — Fuel index source

**Decision:** Authoritative publisher/series, product/region, applicable observation, availability and historical revisions.

**Status:** CONFIRMED — user/business owner, 2026-10-04.

**Options:** A selected: U.S. Energy Information Administration (EIA), Weekly On-Highway Diesel Fuel Price; On-Highway Ultra-Low-Sulfur Diesel (ULSD), USD/gallon, including taxes, WEEKLY. Other publishers/unapproved fallback series are excluded.

**Existing evidence:** No production fuel-index provider, index table, publication selector or ingestion command exists. `FUEL_SURCHARGE` in `AccessorialType` is a charge category, not an index policy. Fuel expense reports record incurred costs; they are not authoritative market index publications. The plan names DOE but does not identify a series, region, fuel product or applicable date.

**Required business answer — received:** Each FSC policy/rate rule explicitly specifies `indexRegion`; there is no global region or implicit California→PADD5→US fallback. Only identifiers actually supported by the provider are allowed; US/PADD1/PADD1A/PADD1B/PADD1C/PADD2/PADD3/PADD4/PADD5/CALIFORNIA were examples, not a blindly approved provider mapping. Select the greatest provider-returned `observationDate <= pricingDate`; never select a future observation. Persist the provider's original observation/publication-period date. Every policy explicitly supplies `maxIndexAgeDays`; age greater than that value returns `FUEL_INDEX_STALE`, no observation returns `FUEL_INDEX_UNAVAILABLE`. Preview may reflect current provider data. Acceptance freezes index value/date/provider/region/series plus retrieval/version metadata; later revisions cannot re-rate accepted history. The business source of `pricingDate` is confirmed by RATE-DEC-001; historical TIMESTAMPTZ-to-business-date provenance needs the adapter decision described there. Provider identifier mapping is a technical verification task against official source, not an invented fallback.

**Code impact:** EIA provider/selection with explicit region and max-age inputs, `FUEL_INDEX_UNAVAILABLE`/`FUEL_INDEX_STALE`, revision provenance and accepted immutable input; no external call within a long financial transaction. No unapproved series, source fallback or seeding example prices. Ingestion mechanics must preserve publication/revision identity and retriable uniqueness if implemented.

**DB impact:** V21+ publication/observation/revision provenance and uniqueness if ingestion is approved; immutable accepted snapshots reference the selected observation/version. No provider URL, seed price or index is inserted before approval.

**Test impact:** Selected region/product/date, publication versus applicability boundary, missing/stale data, approved fallback, revisions preserving historical accepted output, ingestion duplicates/input drift/concurrency if implemented.

**Provider verification evidence (2026-10-04):** [EIA's weekly on-highway diesel table](https://www.eia.gov/dnav/pet/pet_pri_gnd_a_epd2d_pte_dpgal_w.htm) identifies tax-inclusive dollars/gallon and the U.S./PADD/subregion/California publications. Its notes explain the modern all-types series represents ULSD. [EIA API documentation](https://www.eia.gov/opendata/documentation.php) describes provider metadata/facets, version metadata and string-valued data; price values must be parsed as decimal text rather than binary floating point. This confirms provider metadata requirements, not a selected production series mapping: exact region/series support still must be validated against provider facets during adapter implementation. No price examples are seeded or used as policy.

## RATE-DEC-004 — Contract MPG

**Decision:** Versioned MPG/base-price ownership and applicability for approved index-based FSC.

**Status:** CONFIRMED — user/business owner, 2026-10-04.

**Options:** A/C selected as versioned Rate/FSC policy inputs. Fleet-reported or truck actual MPG and global defaults are excluded.

**Existing evidence:** The source has no customer rate/contract MPG/base-price model. Driver pay policies and actual fuel reports belong to other domains. The proposed INDEX_BASED_MPG equation needs both explicit MPG and compatible price/mileage units.

**Required business answer — received:** `contractMpg` and `baseFuelPrice` belong to a versioned Rate/FSC policy. For INDEX_BASED_MPG, `contractMpg > 0` and `baseFuelPrice >= 0`. Store the base price's explicit unit/currency (USD/gallon for the confirmed EIA policy, or the compatible policy unit/currency). Missing values fail with `RATE_MPG_REQUIRED` / `RATE_BASE_FUEL_PRICE_REQUIRED`; invalid values fail with `INVALID_RATE_POLICY`. No truck MPG or global fallback. Acceptance freezes contractMpg, baseFuelPrice, policyId and policyVersion. No extra numeric bounds are invented.

**Code impact:** Resolve validated versioned inputs; `RATE_MPG_REQUIRED`/`RATING_VALIDATION_REQUIRED` for actual missing/invalid-input failures when implemented. No implicit use of report-derived MPG or zero-tax/payroll concepts.

**DB impact:** Columns/relations on the approved policy aggregate, positivity/approved bounds and used-version immutability; no business DEFAULT MPG/base price. Exact DDL waits for owner and precision decisions.

**Test impact:** Exact approved MPG/base source, missing/zero/negative/approved-bound values, dimensional compatibility, effectivity/version change and historical snapshot preservation.

## RATE-DEC-005 — Rounding

**Decision:** Calculation precision, currency scales, modes, rounding boundaries and min/max application.

**Status:** CONFIRMED — RatingPolicyV1, user/business owner, 2026-10-04.

**Options:** Explicit RatingPolicyV1 boundaries selected; this does not change global LogisticsX rounding or prior phases.

**Existing evidence:** `FinancialRoundingPolicy` currently has INVOICE/REPORT/ALLOCATION boundaries configured in `application.yml`; the defaults are HALF_UP and the version is LOGISTICSX-ROUNDING-V1. `MoneyRoundingPolicy` has legacy currency-scale helpers and hard-coded HALF_UP convenience methods. Existing invoice columns use NUMERIC(18,2), while the plan's draft rates use other precisions. Existing runtime behavior is not approval to reuse these settings for new linehaul/FSC intermediates or new currencies. No existing FSC-per-mile, FSC-total or accepted-rating boundary is defined.

**Required business answer — received:** Use BigDecimal with `MathContext.DECIMAL128` for intermediate arithmetic; do not currency-round each intermediate operation. FSC-per-mile is rounded once to scale 6/HALF_UP as an authoritative snapshotted unit rate. FSC total is that rounded rate multiplied by eligible miles, then HALF_UP to currency scale. LINEHAUL, FSC TOTAL, each ACCESSORIAL line, each INVOICE LINE and INVOICE TOTAL round HALF_UP to the minor-unit scale resolved by `CurrencyScaleProvider`; never hard-code scale 2 globally. SUBTOTAL is the sum of rounded invoice lines. TAX comes from a separate tax policy. Compare min/max against the unrounded component amount, select floor/cap, then round the final component. Do not round miles before tier selection. Acceptance records roundingPolicyCode and roundingPolicyVersion. Unsupported currency/invalid contradictory policy inputs must fail validation rather than select a hidden alternative. Tier shapes/percentage pricing formulas still require the approved method contract, not a guessed formula.

**Code impact:** Reuse/extend explicit versioned policy only after approved boundaries are known; calculate without hidden intermediate rounding. Explain raw/component/final results and enforce currency/reconciliation.

**DB impact:** Amount/rate/index/distance/weight precision and snapshot policy data; V21+ only if invoice persistence precision must change. Avoid narrowing historical values.

**Test impact:** Tie values at each boundary, nonterminating division, precision/large values, currency scales, line sum versus invoice total, min/max before/after boundaries, policy-version changes. PostgreSQL numeric storage/reconciliation tests.

## RATE-DEC-006 — Invoice rating snapshot

**Decision:** Acceptance lifecycle, immutable historical fact and invoice reference/correction semantics.

**Status:** CONFIRMED — user/business owner, 2026-10-04.

**Options:** A selected: PREVIEW ephemeral → ACCEPTED immutable RatingSnapshot → INVOICE references the accepted snapshotId. Invoice generation does not re-rate current rules.

**Existing evidence:** V7 `calculation_snapshots` and `CalculationSnapshotService` store entity/policy/engine identity, inputs/results JSONB, currency, checksum, timestamp/actor/correlation. There is no Rating/Quote acceptance aggregate, accepted-rating API or Invoice FK to rating history. `CalculationSnapshotServiceTest` uses RATING as fixture metadata; it does not implement a production rating engine or globally protect accepted rating records. Existing payroll/settlement-specific guards cannot be assumed to protect ratings. Invoice CRUD remains its existing contract.

**Required business answer — received:** Preview creates no immutable financial snapshot. Accept Rating creates immutable RatingSnapshot; invoice generation references that accepted snapshot, without resolving current RateRule again. Snapshot must reproduce/explain pricingDate, ruleId/version/priority, component mileageBasis/source/eligibleMiles, baseRate/minimum/maximum, index provider/series/region/observation date/value and retrieval metadata, baseFuelPrice/contractMpg/FSC-per-mile/FSC-total, accessorial inputs, currency, rounding code/version and subtotal/final result. Correction creates a new snapshot with supersedesSnapshotId, reasonCode, reason, actor and createdAt; it cannot mutate ACCEPTED history. A DRAFT/unissued invoice may regenerate/relink through an idempotent command. ISSUED/POSTED invoices remain historical and require the supplemental or credit/rebill flow in RATE-DEC-007. Existing authorization conventions apply; any additional publishing/acceptance capability is specified with the actual API rather than bypassed.

**Code impact:** Consequential acceptance service, query-only preview, immutable captured inputs/results, invoice mapping to accepted snapshot and explicit correction workflow. Candidate audit fields: rule/policy IDs and versions, component mileage/source, base rate, fuel observation/base/MPG, accessorial inputs, rounding, result, actor/time. Exact fields wait for approval. Retries/concurrency also require RATE-DEC-007.

**DB impact:** Versioned accepted rating and invoice provenance relations, immutable JSONB/audit guards, FK/delete behavior and correction links; the existing generic snapshot table is reused only if it satisfies the confirmed lifecycle.

**Test impact:** Preview does not persist, acceptance actor/auth, snapshot integrity/immutability, later rule/index/mileage changes cannot alter accepted or invoiced values, explicit corrections and concurrent/retried acceptance. PostgreSQL constraints/locking required.

## RATE-DEC-007 — Invoice idempotency

**Decision:** Business identity/granularity for generation, retries and supplemental/credit/rebill outcomes.

**Status:** CONFIRMED — V1 customer freight billing, user/business owner, 2026-10-04.

**Options:** One PRIMARY per tenant/load/customer/currency, plus explicitly linked SUPPLEMENTAL and credit/rebill documents. Customer-period billing was not selected. A request idempotency key and business uniqueness are separate invariants.

**Existing evidence:** V1 has the unique index `ix_invoices_load_id`; `Invoice.load` is mapped OneToOne. This currently allows at most one invoice with each non-null load ID; invoices with null load_id do not share that invariant. Controller `POST /api/invoices` is general CRUD, and `InvoiceService.create` has no rating-generation idempotency command. Therefore the existing index is a compatibility constraint, not proof that the business has approved all future billing/correction scenarios.

**Required business answer — received:** PRIMARY business key is tenant + load + customer + currency; keep customer identity in the invariant/audit even though Load already belongs to Customer. No second PRIMARY for that key. SUPPLEMENTAL is permitted for late accessorial, approved post-delivery charge or approved correction not requiring full rebill; it has `parentInvoiceId = primaryInvoiceId`, `invoicePurpose = SUPPLEMENTAL`. Credit/rebill is permitted as original→credit memo/invoice→rebill with the reference chain preserved; never edit issued history. Generation requires `idempotencyKey` unique within tenant + operation and a stored normalized input hash. Same key/same normalized input returns existing result. Same key/different normalized input returns HTTP 409 `INVOICE_IDEMPOTENCY_CONFLICT` and creates no new invoice. Source accepted snapshot is used. Under DATABASE_PER_TENANT, tenant scope is physical; uniqueness within that tenant DB must include the approved remaining identity dimensions.

**Code impact:** Dedicated generation/acceptance command and canonical input comparison, atomic invoice/lines/snapshot reference, stale/conflict handling and replay. Existing generic CRUD is not silently redefined. Reuse a transactional outbox only where an established implementation and actual cross-domain side effect require it.

**DB impact:** V21+ deterministic business-key uniqueness and immutable provenance. If multiple invoices per load are approved, forward migration must reconcile the unique load index and OneToOne mapping while preserving historical references; do not drop it based on a draft option.

**Test impact:** Same-input replay, differing-input key conflict, deterministic uniqueness, concurrent generation/acceptance, approved supplemental/credit/rebill flow, rollback and historical invoice protection. PostgreSQL integration is mandatory for uniqueness and locking.

## Completed independent preparation and implementation boundaries

- Whitespace-only quality fix and complete baseline regression on an isolated PostgreSQL clone.
- Persist this decision register, source evidence and 6A→6G/test/migration dependency mapping; update the plan/checkpoint/memory to the verified Phase 5 state.
- Make the existing regression procedure reproducible in the repository and fail if PostgreSQL methods are skipped or the legacy skip budget changes.
- Preparation added no speculative business stubs, schema or public pricing endpoint. The completed RATE-DEC gate authorizes incremental 6A→6G implementation. User explicitly limited V1 to FLAT/PER_MILE linehaul and INDEX_BASED_MPG FSC; advanced methods are deferred.

## Implementation and release gates

1. Confirm complete answers for every applicable RATE-DEC item. Publish exact supported methods, dimensions, currencies, formulas and correction rules. Confirm NOT_APPLICABLE explicitly where appropriate.
2. 6A schema/domain/effective versions → 6B deterministic resolution → 6C explicit mileage → 6D approved FSC → 6E explainable rating → 6F accepted immutable snapshot → 6G transactional/idempotent invoice integration. Progress is recorded after each sub-task; schema/classes alone do not complete Phase 6.
3. Every migration batch is V21+, with clean bootstrap, previous-version populated clone upgrade and Flyway validation. V1–V20 checksums and historical source databases remain unchanged.
4. Baseline gate: at least 192 Surefire-reported tests (current baseline: 191 executed, one named legacy skip), zero failures/errors, exactly the existing `LogicsticApplicationTests.contextLoads` skip, at least 40 executed PostgreSQL integration methods and no PostgreSQL skips. Add meaningful tests for each implemented business change; never replace PostgreSQL behaviors with H2.
5. Phase 6 adds precedence/ambiguity/effectivity/currency/mileage/index/MPG/rounding/snapshot/idempotency/concurrency coverage. Follow existing domain errors, tenant/auth rules and observability stack; do not add a second metrics framework or external calls within long DB transactions.
6. Phase 7 remains gated by COMPLETE Phase 5 and Phase 6. Reuse the existing HOS subsystem through a confirmed feasibility integration; no simplified drive-minutes score, fake candidates or guessed utility weights.
7. Phase 8 requires approved historical event interval/productive/available/excluded semantics, duplicate/tie/missing/open/out-of-order rules and authoritative sources for fleet health. Missing source/denominator is UNAVAILABLE. Existing loaded/empty fields do not approve a new denominator; no historical utilization from current truck status.

The backend plan remains incomplete until Phases 6–8 and final migration/regression/quality gates pass.
