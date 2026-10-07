-- V36: Add idempotency, input hash, and version to payments for financial integrity
ALTER TABLE payments ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(200);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS input_hash VARCHAR(64);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_idempotency_key
ON payments(idempotency_key)
WHERE idempotency_key IS NOT NULL;
