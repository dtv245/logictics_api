-- V1-V24 are immutable. No historical invoice/tax/date values are inferred.
CREATE TABLE invoice_tax_assessments (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL REFERENCES loads(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    source_type TEXT NOT NULL CHECK (source_type IN ('ACCOUNTING','TRUSTED_EXTERNAL_TAX_PROVIDER')),
    source_reference TEXT NOT NULL CHECK (length(btrim(source_reference)) BETWEEN 1 AND 1000),
    jurisdiction TEXT NOT NULL CHECK (length(btrim(jurisdiction)) BETWEEN 1 AND 1000),
    taxable_basis NUMERIC NOT NULL CHECK (taxable_basis >= 0 AND taxable_basis NOT IN ('NaN','Infinity','-Infinity')),
    tax_amount NUMERIC NOT NULL CHECK (tax_amount >= 0 AND tax_amount NOT IN ('NaN','Infinity','-Infinity')),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    policy_version TEXT CHECK (policy_version IS NULL OR length(btrim(policy_version)) BETWEEN 1 AND 1000),
    assessed_at TIMESTAMPTZ NOT NULL,
    assessed_by TEXT NOT NULL CHECK (length(btrim(assessed_by)) BETWEEN 1 AND 1000),
    captured_by UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    captured_at TIMESTAMPTZ NOT NULL,
    input_hash VARCHAR(64) NOT NULL CHECK (input_hash ~ '^[0-9a-f]{64}$')
);
CREATE FUNCTION protect_invoice_tax_assessment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Tax assessment is immutable' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM loads WHERE id=NEW.load_id AND customer_id=NEW.customer_id) THEN
        RAISE EXCEPTION 'Tax assessment Load/customer mismatch' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_tax_assessment_guard
    BEFORE INSERT OR UPDATE OR DELETE ON invoice_tax_assessments
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_tax_assessment();
