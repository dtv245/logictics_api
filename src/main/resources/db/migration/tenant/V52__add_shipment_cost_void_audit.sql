-- Voiding a cost is an audited financial command: who voided it, when, and why.
-- The audit columns stay NULL for every non-voided row; the CHECK is NOT VALID so
-- pre-existing VOIDED rows written before this command existed are not rejected.
ALTER TABLE shipment_costs ADD COLUMN voided_at TIMESTAMPTZ NULL;
ALTER TABLE shipment_costs ADD COLUMN voided_by UUID NULL;
ALTER TABLE shipment_costs ADD COLUMN void_reason VARCHAR(500) NULL;

ALTER TABLE shipment_costs ADD CONSTRAINT ck_shipment_costs_void_audit
    CHECK (status <> 'VOIDED' OR (voided_at IS NOT NULL AND void_reason IS NOT NULL))
    NOT VALID;
