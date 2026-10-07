# Accepted Rating V1 snapshots

6F COMPLETE: implements RATE-DEC-005/006 and retriable acceptance; V24 is additive.

Preview remains ephemeral. Its input/result fingerprints describe commercial
inputs and outcomes, using canonical object ordering and exact decimal values.
Correlation/calculation/retrieval instants do not change a commercial result
fingerprint; index price/date/series/provider version/content hash do. An EIA
revision therefore requires an explicit new preview/acceptance, not rerating.

`POST /api/loads/{id}/rating/accept` requires ADMIN/ACCOUNTANT and a mapped
authenticated tenant employee. Request: idempotencyKey, explicit rating request,
expectedInputHash/expectedResultHash from preview; optionally supersedesSnapshotId
with explicit reasonCode/reason. Actor is never supplied by the client.

Acceptance first looks for operation/key replay. Same normalized command returns
the original immutable outcome, even after current Load/rule/provider changes.
Changed command on that key fails 409 RATING_IDEMPOTENCY_CONFLICT. Without an
existing outcome, fresh preview/provider retrieval happens outside the write
transaction; changed fingerprints fail 409 RATING_PREVIEW_STALE without a row.

Write transaction obtains a key advisory lock, rechecks replay, locks Load and
explicit selected charges, and takes a shared publication-table lock. Concurrent
acceptances can proceed; publication inserts cannot change resolution during the
short commit window. Inputs are reloaded/revalidated before persistence. No
provider HTTP or external side effect occurs in this transaction. The key lock
orders same-key races; database operation/key uniqueness is a separate invariant.

`accepted_rating_snapshots` retains exact DATE/source/change audit, Load/customer/
currency and exact currencyScale, rule/version, rounding policy/version, unrestricted exact subtotal,
full JSONB calculation/input/result explanation, fingerprints, actor/time and
normalized command hash. DB guard checks current Load date/audit/customer,
effective rule/currency, scalar/payload identity and sum of rounded lines.
Updates/deletes reject. Correction appends a new snapshot referencing the old
one, with the same Load/customer/currency and an explicit coded reason/context.
No inferred single-root business uniqueness was added for accepted ratings; the
approved PRIMARY invoice business invariant is distinct and belongs to 6G.

`GET /api/rating/snapshots/{id}` is guarded by the existing SecurityConfig route
architecture (not only method annotations). Reads do not re-rate or write.
Issued invoice historical changes, tax assessment, generation and credit/rebill
commands belong to 6G. No generic calculation snapshot is misrepresented as
financial acceptance; no outbox is needed for this persistence-only command.

Real PostgreSQL tests cover exact business date (separate from offset timestamp),
Load/rule changes and append-only correction, immutable/reconciled DB payload,
same-key concurrency/replay/conflict, stale date/index revision, frozen accepted
index inputs, source context and actual API actor/403 read protection. Clean
latest, populated V23→V24 and Flyway validate are required; no new skipped test.

Final verification on 2026-10-04: clean codex_regression_20261004065734077168
(/tmp/logisticsx-regression-kp09gd53) and populated V23→V24 clone
codex_regression_20261004065914986712 (/tmp/logisticsx-regression-pefyz2ee):
267 reported / 266 executed / zero failures/errors / one named legacy skip,
67 real PostgreSQL methods. Two fingerprint unit and six snapshot PG/API methods
added. Existing V1–V23 SHA-256 unchanged; V24 unchanged after first migration gate.
