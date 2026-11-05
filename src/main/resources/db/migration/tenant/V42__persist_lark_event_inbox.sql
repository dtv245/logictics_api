-- Upgrade existing receipts without replaying events that have no saved payload.
ALTER TABLE lark_event_receipts ADD COLUMN event_type VARCHAR(255);
ALTER TABLE lark_event_receipts ADD COLUMN payload_json TEXT;
ALTER TABLE lark_event_receipts ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETE';
ALTER TABLE lark_event_receipts ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE lark_event_receipts ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE lark_event_receipts ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE lark_event_receipts ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE lark_event_receipts ADD CONSTRAINT ck_lark_event_receipt_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'COMPLETE', 'DEAD'));
ALTER TABLE lark_event_receipts ADD CONSTRAINT ck_lark_event_receipt_attempts CHECK (attempts >= 0);
ALTER TABLE lark_event_receipts ADD CONSTRAINT ck_lark_event_receipt_pending_payload
    CHECK (status IN ('COMPLETE', 'DEAD')
        OR (event_type IS NOT NULL AND payload_json IS NOT NULL AND next_attempt_at IS NOT NULL));
CREATE INDEX idx_lark_event_receipts_due ON lark_event_receipts(status, next_attempt_at);
CREATE INDEX idx_lark_event_receipts_lease ON lark_event_receipts(status, locked_until);
CREATE INDEX idx_lark_event_receipts_completed ON lark_event_receipts(status, completed_at);
