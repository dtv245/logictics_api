# Rating V1 — published policy domain (6A)

Business authority: [rating-policy-decisions.md](rating-policy-decisions.md). Implementation source: V21 and `service/rating`. Convention Phases 0–5 remain complete.

Deferred/unknown linehaul method input returns UNSUPPORTED_RATE_METHOD, including
through JSON binding. Only FLAT/PER_MILE enum values enter V1 schema/calculation;
no deferred placeholder or implicit fallback is persisted.

## Published versions

Contract identity belongs to one customer. Each explicit version records currency, inclusive LocalDate effective interval, authenticated employee and timestamp. Rate rule identity has append-only published versions with priority, six nullable match dimensions, dates, currency and component pricing configuration. A contract-scoped rule pins an explicit contract version and must agree with its customer/currency/effective interval.

All published policy versions are immutable immediately, including before first acceptance; authoring edits append a version. This technical safeguard is stronger than merely protecting used versions. A family identity row is locked while appending; an explicit `expectedVersion` prevents stale concurrent writes. No version automatically mutates the previous interval. Overlapping effective versions remain separate candidates; the resolver must return ambiguity at the winning priority, not choose the newest. To author successive nonoverlapping periods, publish their explicit finite/inclusive intervals.

There is no draft/retirement state machine or automatically inferred policy priority. Family identity UUIDs are not financial request idempotency keys; consequential rating/invoice retry safety belongs to 6F/6G.

## V1 inputs

- Linehaul FLAT/PER_MILE only. PER_MILE requires an explicit mileage basis; FLAT carries no mileage basis. Minimum charge is a monetary floor, not a mileage source.
- Optional FSC configuration is explicit INDEX_BASED_MPG. Absent configuration means no FSC policy was authored, not a fake index or unavailable fuel value turned into zero.
- EIA published regions supported in the policy model: US, PADD1/PADD1A/PADD1B/PADD1C, PADD2/PADD3/PADD4/PADD5, CALIFORNIA. Exact provider series mapping/selection is a separate 6D implementation gate; the authoring model does not claim any live observation was fetched.
- EIA V1 input prices are USD/gallon; rating currency must be compatible USD. Non-USD index conversion is not implemented or assumed. Non-FSC contracts/rules can carry explicit three-letter currency; calculation later requires a qualified CurrencyScaleProvider.
- Policy decimals use unrestricted PostgreSQL NUMERIC and Java BigDecimal so authoring does not silently round MPG, base rate or fuel prices. Currency rounding occurs at the confirmed engine boundaries, not while publishing.
- RatingPolicyV1 code/version are recorded explicitly by the V1 publisher. No changes to the earlier global rounding policy.
- Tier is an exact match dimension; it does not enable the deferred TIERED rate method or infer tier boundaries.

## Authoring API

| Operation | Endpoint |
|---|---|
| Publish contract v1 | POST /api/rating/contracts |
| Append contract version | POST /api/rating/contracts/{id}/versions?expectedVersion=N |
| Read contract version | GET /api/rating/contracts/{id}/versions/{version} |
| Publish rule v1 | POST /api/rating/rules |
| Append rule version | POST /api/rating/rules/{id}/versions?expectedVersion=N |
| Read rule version | GET /api/rating/rules/{id}/versions/{version} |

ADMIN/ACCOUNTANT financial-policy roles follow existing financial authoring conventions. Publish actor is resolved from authentication to an employee in the routed tenant database; no caller-supplied actor/tenant identity is accepted. Read responses use the existing ApiResponse envelope.

Errors reuse ApiException: invalid inputs INVALID_RATE_POLICY, explicit missing MPG/base-price codes, missing persisted actor RATING_ACTOR_REQUIRED, stale version HTTP 409 RATE_POLICY_VERSION_STALE; missing resources use the existing not-found contract. Unknown advanced methods are rejected at DTO enum binding, not routed to zero-valued calculators.

## Persistence gate

V21 is additive, without altering V1–V20 or legacy Invoice/Payroll data. FKs guard customer/actor/contract context; checks guard periods, supported methods, nonnegative finite decimal inputs, ordered min/max and complete FSC inputs. SQL UPDATE/DELETE of published history is rejected. Overlapping matching rules are deliberately allowed in storage and must be rejected as ambiguous by 6B; no unique index silently encodes specificity.

6A is policy authoring/domain only. It does not claim mileage availability, provider integration, rating preview, immutable acceptance or invoice generation completion.

## 6B resolver contract

`RateRuleResolver.resolve(RateMatchContext)` takes explicit business LocalDate and customer/context. All configured dimensions must exactly match; null rule dimensions are wildcards. Effective endpoints are inclusive. Choose the smallest integer priority; multiple winning rows produce RATE_RULE_AMBIGUOUS regardless of specificity, version or database order. Lower-priority ties do not affect a unique winning row.

Winner currency must equal requested billing currency; incompatible winner fails CURRENCY_MISMATCH, never silently selects a lower-priority rate. Contract context must pin a valid version, agree with customer/currency and be effective on pricingDate. A winning contract-scoped rule must reference that version; no automatic latest-version lookup.

Missing date is RATING_PRICING_DATE_REQUIRED; no matching row is RATE_RULE_NOT_FOUND. No clock or timezone conversion is used. The resolver is internal and read-only; it is not a public arbitrary-date rating endpoint. User confirmed the independent Load business DATE/no backfill contract; V22 implements the audited adapter. V23 adds immutable per-component effective-contract mileage evidence. See [load-pickup-date-and-rating-mileage.md](load-pickup-date-and-rating-mileage.md).
