# Payment commands

POST `/api/payments` requires an invoice, stable retry key (at most 200 characters), PENDING status, positive NUMERIC(18,2) amount and supported currency. The server assigns recorded actor/time; a supplied recordedAt is rejected. Accounting role and a mapped employee in the authenticated tenant are mandatory.

The tenant-local advisory retry-key lock precedes key lookup; invoice row lock precedes balance validation and payment writes. PENDING reserves balance; COMPLETED/PAID/SUCCEEDED/SETTLED consume it; CANCELLED/VOID release it. Unknown legacy states reject new financial commands. Same key and normalized creation input replays one payment ID; changed input returns 409 PAYMENT_IDEMPOTENCY_CONFLICT. Insufficient invoice balance returns 422 PAYMENT_EXCEEDS_INVOICE_BALANCE.

PAYMENT_CREATE_V1 fingerprints all accepted creation fields, including billing metadata, description/reference and provider IDs. Currency and decimal representation are canonical; key and server actor/time are excluded. Optional absent/null fields have the same no-value meaning. Existing null-format hashes retain their original invoice/amount/currency interpretation and are never rewritten. Metadata editing does not rewrite creation fingerprints.

PUT accepts description/referenceNumber only. Omitted metadata is retained; explicit null clears that metadata field. Every other supplied property returns 409 PAYMENT_FINANCIAL_FIELDS_IMMUTABLE. Only PENDING metadata may change. Cancellation requires a reason and permits PENDING only; retry preserves the first cancellation event. DELETE remains deprecated and always rejects 409 PAYMENT_DELETE_FORBIDDEN for an authorized actor.

V38 adds immutable command events and SQL guards. New inserts require financial command identity; every creation/change needs matching actor/state/version evidence before commit. Existing rows are not reclassified or rehashed. Payment, audit and balance effects roll back together. No provider completion, refund or settled reversal command is introduced.

The legacy payments.tenant_id UUID remains opaque compatibility data. No active Java query uses it as the tenant boundary; physical datasource routing controls isolation. External legacy consumers still need audit before a future semantic migration. The observed running database has zero Payment rows, so no legacy repair is applied.

The user subsequently approved the SETTLED reporting follow-up on 2026-10-07. Paid/open-balance calculators now include case-insensitive SETTLED alongside COMPLETED/PAID/SUCCEEDED. PENDING reserves balance without counting as paid. A USD 100 invoice with a USD 40 SETTLED payment reports paid 40/open 60. Actual PostgreSQL HTTP and populated V37→V38 fixtures verify unchanged historical rows, currency/role/tenant boundaries and no fabricated audit events. Commands still create PENDING/cancel to CANCELLED only; no historical status rewrite or provider completion command was added. See docs/verification/backend-remediation-followup-evidence-20261007.json. No production certification is implied.

Release requires frontend stable-key/metadata/cancel readiness, provisioned signing key, schema validation, verified artifact identity and a secure authorized rollout. The running application/database are not changed by implementation tests.
