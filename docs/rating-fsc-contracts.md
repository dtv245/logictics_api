# Customer rating and fuel surcharge contract

Decision status: CONFIRMED. Implementation: Phase 6 IN_PROGRESS, not COMPLETE.

Canonical contract: [rating-policy-decisions.md](rating-policy-decisions.md), RATE-DEC-001…007, confirmed by the business owner on 2026-10-04. This supersedes the earlier OPEN discovery draft.

- V1 linehaul: FLAT/PER_MILE. FSC: explicitly configured INDEX_BASED_MPG. Other methods are deferred by the user.
- Smaller explicit priority wins; winning tie is ambiguous without specificity. Requested pickup business LocalDate; inclusive dates; exact/null-wildcard dimensions.
- Component/contract mileage needs explicit load attribution and provenance. No legacy distance or invented Trip allocation.
- EIA weekly on-highway ULSD, supported explicit region and maximum age. No default MPG/base price or stale fallback.
- RatingPolicyV1: DECIMAL128 intermediates; FSC unit rate scale 6 HALF_UP; currency-scale line boundaries; unrounded min/max.
- Preview ephemeral; acceptance immutable; correction creates a superseding snapshot. Invoice references acceptance and does not re-rate.
- PRIMARY uniqueness and operation-scoped normalized-input idempotency are separate guards. Supplemental/credit/rebill preserve links and issued history.
- Database-per-tenant, V21+ and prior phase invariants remain unchanged. Existing Invoice CRUD/generic snapshots do not prove rating-generation completion.

Sequence: 6A schema/domain → 6B resolver → 6C mileage → 6D FSC → 6E explainable rating → 6F immutable acceptance → 6G invoice integration. Each slice requires added tests and regression/PostgreSQL/migration gates.
