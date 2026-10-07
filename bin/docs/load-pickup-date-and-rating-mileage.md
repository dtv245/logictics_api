# Load pickup business date and Rating V1 mileage

Authority: user decision 2026-10-04 and RATE-DEC-001/002. This is Phase 6 continuation, not a reopening of Phases 0–5.

## Independent pickup date (V22)

The existing Java/API `requestedPickupDate` and SQL `requested_pickup_date` are OffsetDateTime/TIMESTAMPTZ legacy appointment instants. They remain unchanged for compatibility. The independent LocalDate field is `requestedPickupBusinessDate`, stored as nullable DATE `requested_pickup_business_date`. Its meaning is the business requested pickup date, not a generic pricing_date on Load. Rating reads this date only; there is no persisted instant-to-date derivation.

Create/update Load accepts explicit requestedPickupBusinessDate and requestedPickupDateProvenance `{reasonCode, reason, source}`. No timezone inference, systemDefault or DB/JVM timezone is used. Omission leaves the business date unchanged on update and NULL on creation. The old timestamp can change independently. Clearing a proven date is not implicitly supported by a null/omitted field.

Business-date capture needs ADMIN/ACCOUNTANT/DISPATCHER and an authenticated employee in the routed tenant database. A changed value appends an immutable chain with prior value/change ID, new date, actor, correctedAt, reason code/text and source. The Load points to its current correction; PostgreSQL rejects unaudited rewrites and old audit replay. Published history prevents physical deletion of that audited Load through the existing deletion endpoint; return LOAD_PICKUP_DATE_HISTORY_PROTECTED rather than losing evidence.

Explicit historical remediation: POST `/api/loads/{id}/requested-pickup-business-date`, body `{requestedPickupBusinessDate, provenance, expectedChangeId}`. Missing prior history uses expectedChangeId null; subsequent corrections require the current change ID and reject stale commands with LOAD_PICKUP_DATE_CONFLICT. This is intentional correction, not migration backfill.

Historical rows keep NULL. V22 contains no UPDATE/backfill from TIMESTAMPTZ. `LoadRatingContextService.pricingDate` rejects missing business date with RATING_PRICING_DATE_REQUIRED, regardless of appointment, dispatch, createdAt, invoice or current date. Its immutable input records exact pricingDate, pricingDateSource = LOAD_REQUESTED_PICKUP_DATE and the audit change ID. The later 6F accepted snapshot must persist these fields unchanged; a captured input object is not itself an accepted financial snapshot.

## Verification scope

Required date gates: create/update exact date, offset-boundary independence, timestamp-only history remains NULL, no fallback, explicit audited remediation, stale/auth rejection, PostgreSQL history guards and clean + populated V21→V22 migration validation. Accepted financial snapshot persistence remains a 6F gate until that aggregate exists; do not fake an acceptance test against an input record.

## Explicit contract mileage (6C / V23)

POST `/api/loads/{id}/rating/contract-mileage` publishes agreement evidence for one component. ADMIN/ACCOUNTANT author identity comes from authenticated tenant employee; request supplies componentType, contractId/version, originalValue, originalUnit and agreement provenance. Authoring pins a real effective customer agreement, not a bare mileage number with a caller-created source label. Pricing date must already be proven by the Load DATE adapter. The contract FK/source version, original input, exact normalized miles, actor and capturedAt are immutable. Correction captures new evidence; no automatic newest-evidence selection.

Canonical unit is MILE. V1 accepts authored MILE only; other conversions are not silently guessed. Distances follow existing NUMERIC(12,3) precision. Negative, overflow and nonrepresentable fractions reject instead of rounding before a tier; harmless trailing zeros retain the original decimal. PostgreSQL equality between original and normalized values catches implicit NUMERIC scale rounding even outside Java.

LINEHAUL, FSC and RATE_TIER evidence is component-specific. The current V1 rating methods do not calculate TIERED rates; the RATE_TIER provenance label does not enable that deferred method. LINEHAUL/FSC basis must equal the selected versioned policy configuration. MINIMUM_CHARGE returns NOT_APPLICABLE with no eligibleMiles, not a fake zero-distance input. Consumers explicitly select an evidence ID per component, never infer it by row order or latest capture.

Resolution checks evidence Load/component, effective agreement customer/date, currency and selected rule contract/version. Its result carries componentType, mileageBasis/sourceType CONTRACT, contract reference/version, original value/unit, eligibleMiles, evidence ID/provenance/actor/time for accepted snapshots.

PLANNED_LOAD_MILES, ACTUAL_LOADED_MILES and ACTUAL_ALL_MILES currently have no qualified Load-level route/movement-leg adapter. They return RATE_MILEAGE_UNAVAILABLE. If a related Trip contains multiple distinct Loads without qualified attribution, return RATE_MILEAGE_ATTRIBUTION_REQUIRED. No equal/count/revenue/weight allocation, legacy `loads.distance`, or Trip planned/actual/loaded/empty fallback is used. Explicit contract evidence remains usable for a multi-load Trip because it is already Load-attributed. Zero contract miles is a valid zero source, not unavailable input.

6C does not claim planned/actual source ingestion, accepted financial snapshots or invoice generation. Their later approved integration must consume these explainable inputs without mutating evidence.
