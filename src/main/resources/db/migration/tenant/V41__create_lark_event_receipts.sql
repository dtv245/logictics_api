-- Tenant-local, atomic event claims. No business payload or credential is stored.
CREATE TABLE lark_event_receipts (
    event_id VARCHAR(255) PRIMARY KEY,
    claim_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_lark_event_receipts_expires_at ON lark_event_receipts (expires_at);
