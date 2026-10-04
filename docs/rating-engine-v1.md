# Explainable Rating V1

6E uses confirmed RATE-DEC-001…005; no additional rating method or tax formula.

`POST /api/loads/{id}/rating/preview` requires ADMIN/ACCOUNTANT. It receives
explicit contract/version, lane/equipment/service/tier request context and
`contextSource`, currency, linehaul/FSC evidence IDs and an explicit list of
accessorial IDs (empty means explicitly no selected accessorial). Customer and
pricing date cannot be supplied: they come from the persisted Load and its
independent audited business DATE. Do not derive equipment from legacy FTL/LTL
load type, lane from an unapproved geocoding convention or date from appointment.

Load/read transaction returns immutable inputs before any EIA HTTP retrieval.
Preview is ephemeral: no snapshot, invoice, charge status or ledger mutation.
Structured existing SLF4J logging records correlation, rule/policy version,
duration and success/domain error. No second metrics framework introduced.

FLAT uses rule base amount, no mileage. PER_MILE uses exact per-component miles.
Linehaul applies minimum/maximum to raw value before currency boundary rounding.
Configured FSC consumes its independently resolved mileage and 6D engine.
Selected accessorials must be approved, audited and belong to the Load; billing
uses only customerAmount, never companyCost/driverPay. Each amount rounds at
its line boundary; subtotal is the exact sum of rounded lines. Selection is
ordered by stable charge ID and rejects duplicates, foreign/pending/invoiced
charges. A selected manual fuel charge together with computed FSC rejects for
explicit reconciliation instead of double charging or silently dropping it.

Output retains full context/date source/audit, selected rule/version/priority,
all component provenance, raw/bounded linehaul, raw/rounded FSC, approved charge
inputs/version/audit, individual money lines and subtotal. `taxAvailability`
is `NOT_INCLUDED_SEPARATE_POLICY`: subtotal is pre-tax, not a fake tax=0/final
tax-inclusive invoice total. Persisted acceptance belongs to 6F.

Verification: four exact-decimal/min-max/rounding/source unit cases and three
real PostgreSQL full-runtime preview/read-only/date/accessorial/API/role methods.
All earlier tests must remain green. No schema change needed for 6E.
