-- Forward-only financial adaptation. No legacy invoice purpose/tax is fabricated.
ALTER TABLE invoices
    ALTER COLUMN subtotal_amount TYPE NUMERIC,
    ALTER COLUMN tax_total_amount TYPE NUMERIC,
    ALTER COLUMN total_amount TYPE NUMERIC,
    ADD COLUMN invoice_purpose TEXT,
    ADD COLUMN economic_sign SMALLINT,
    ADD COLUMN rating_snapshot_id UUID,
    ADD COLUMN parent_invoice_id UUID REFERENCES invoices(id) ON DELETE RESTRICT,
    ADD COLUMN billing_chain_id UUID REFERENCES invoices(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    ADD COLUMN billing_command_id UUID,
    ADD COLUMN tax_requirement TEXT,
    ADD COLUMN tax_decision JSONB,
    ADD COLUMN tax_assessment_id UUID REFERENCES invoice_tax_assessments(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_invoice_accepted_rating
      FOREIGN KEY(rating_snapshot_id,load_id,customer_id,subtotal_currency)
      REFERENCES accepted_rating_snapshots(id,load_id,customer_id,currency) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_rated_invoice_identity CHECK (invoice_purpose IS NULL OR (
      invoice_purpose IN ('PRIMARY','SUPPLEMENTAL','CREDIT','REBILL') AND economic_sign IS NOT NULL
      AND economic_sign=CASE WHEN invoice_purpose='CREDIT' THEN -1 ELSE 1 END
      AND load_id IS NOT NULL AND customer_id IS NOT NULL AND billing_chain_id IS NOT NULL
      AND billing_command_id IS NOT NULL AND tax_requirement IN ('REQUIRED','NOT_REQUIRED')
      AND tax_requirement IS NOT NULL AND tax_decision IS NOT NULL
      AND subtotal_currency=tax_total_currency AND subtotal_currency=total_currency
      AND subtotal_amount>=0 AND tax_total_amount>=0 AND total_amount=subtotal_amount+tax_total_amount
      AND subtotal_amount NOT IN ('NaN','Infinity','-Infinity') AND tax_total_amount NOT IN ('NaN','Infinity','-Infinity')
      AND (invoice_purpose<>'CREDIT' OR total_amount>0)
      AND (invoice_purpose<>'PRIMARY' OR (parent_invoice_id IS NULL AND rating_snapshot_id IS NOT NULL AND billing_chain_id=id))
      AND (invoice_purpose='PRIMARY' OR parent_invoice_id IS NOT NULL)
      AND (invoice_purpose='CREDIT' OR rating_snapshot_id IS NOT NULL)
      AND (tax_requirement<>'REQUIRED' OR tax_assessment_id IS NOT NULL)
      AND (tax_assessment_id IS NOT NULL OR tax_total_amount=0)
    ));
CREATE UNIQUE INDEX ux_rated_primary_business_key ON invoices(load_id,customer_id,subtotal_currency)
    WHERE invoice_purpose='PRIMARY';
-- Keep the original one-invoice index until signed consumers/multiplicity are ready.
ALTER TABLE invoice_line_items
    ALTER COLUMN amount_amount TYPE NUMERIC,
    ALTER COLUMN tax_amount TYPE NUMERIC,
    ALTER COLUMN tax_rate_percent DROP NOT NULL,
    ADD COLUMN rating_source_id UUID,
    ADD COLUMN credited_line_id UUID REFERENCES invoice_line_items(id) ON DELETE RESTRICT,
    ADD COLUMN credited_quantity NUMERIC;
CREATE TABLE invoice_billing_commands (
    id UUID PRIMARY KEY,
    operation TEXT NOT NULL CHECK (length(btrim(operation)) BETWEEN 1 AND 80),
    idempotency_key VARCHAR(120) NOT NULL CHECK (length(btrim(idempotency_key))>0),
    normalized_input_hash VARCHAR(64) NOT NULL CHECK (normalized_input_hash ~ '^[0-9a-f]{64}$'),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    input JSONB NOT NULL CHECK (jsonb_typeof(input)='object'),
    outcome JSONB NOT NULL CHECK (jsonb_typeof(outcome)='object'),
    actor UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    captured_at TIMESTAMPTZ NOT NULL,
    UNIQUE(operation,idempotency_key)
);
ALTER TABLE invoices ADD CONSTRAINT fk_invoice_billing_command FOREIGN KEY(billing_command_id)
    REFERENCES invoice_billing_commands(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION protect_invoice_billing_command() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Invoice command audit is immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER trg_invoice_billing_command_immutable BEFORE UPDATE OR DELETE ON invoice_billing_commands
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_billing_command();

CREATE FUNCTION protect_rated_invoice_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' AND OLD.invoice_purpose IS NOT NULL THEN
        RAISE EXCEPTION 'Rated financial invoice history cannot be deleted' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND OLD.invoice_purpose IS NOT NULL THEN
        IF NEW.invoice_purpose IS DISTINCT FROM OLD.invoice_purpose
           OR NEW.load_id IS DISTINCT FROM OLD.load_id OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
           OR NEW.subtotal_currency IS DISTINCT FROM OLD.subtotal_currency
           OR NEW.economic_sign IS DISTINCT FROM OLD.economic_sign
           OR NEW.parent_invoice_id IS DISTINCT FROM OLD.parent_invoice_id
           OR NEW.billing_chain_id IS DISTINCT FROM OLD.billing_chain_id THEN
            RAISE EXCEPTION 'Invoice business identity is immutable' USING ERRCODE='23514';
        END IF;
        IF OLD.status<>'DRAFT' AND (
           NEW.status NOT IN ('ISSUED','SENT','PARTIALLY_PAID','PAID')
           OR ROW(NEW.subtotal_amount,NEW.tax_total_amount,NEW.total_amount,NEW.rating_snapshot_id,
                  NEW.tax_requirement,NEW.tax_decision,NEW.tax_assessment_id,NEW.billing_command_id)
              IS DISTINCT FROM ROW(OLD.subtotal_amount,OLD.tax_total_amount,OLD.total_amount,OLD.rating_snapshot_id,
                  OLD.tax_requirement,OLD.tax_decision,OLD.tax_assessment_id,OLD.billing_command_id)) THEN
            RAISE EXCEPTION 'Issued invoice financial history is immutable' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_rated_invoice_history BEFORE UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION protect_rated_invoice_history();

CREATE FUNCTION guard_rated_invoice_line() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_invoice invoices;
BEGIN
    SELECT * INTO v_invoice FROM invoices WHERE id=CASE WHEN TG_OP='DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END FOR UPDATE;
    IF v_invoice.invoice_purpose IS NOT NULL THEN
        IF v_invoice.status<>'DRAFT' THEN
            RAISE EXCEPTION 'Issued invoice lines are immutable' USING ERRCODE='23514';
        END IF;
        IF TG_OP<>'DELETE' AND (NEW.amount_amount<0 OR NEW.tax_amount<0 OR NEW.rating_source_id IS NULL
            OR NEW.amount_currency<>v_invoice.subtotal_currency
            OR NEW.amount_amount IN ('NaN','Infinity','-Infinity') OR NEW.tax_amount IN ('NaN','Infinity','-Infinity')) THEN
            RAISE EXCEPTION 'Invalid rated invoice financial line' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_rated_invoice_line BEFORE INSERT OR UPDATE OR DELETE ON invoice_line_items
    FOR EACH ROW EXECUTE FUNCTION guard_rated_invoice_line();

CREATE FUNCTION reconcile_rated_invoice() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_id UUID; v invoices; c invoice_billing_commands; a invoice_tax_assessments;
BEGIN
    IF TG_TABLE_NAME='invoices' THEN v_id=NEW.id;
    ELSE v_id=CASE WHEN TG_OP='DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END; END IF;
    SELECT * INTO v FROM invoices WHERE id=v_id;
    IF NOT FOUND OR v.invoice_purpose IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO c FROM invoice_billing_commands WHERE id=v.billing_command_id;
    IF NOT FOUND OR c.invoice_id<>v.id OR c.outcome->>'invoiceId'<>v.id::text
       OR (c.outcome->>'subtotal')::NUMERIC<>v.subtotal_amount
       OR (c.outcome->>'tax')::NUMERIC<>v.tax_total_amount
       OR (c.outcome->>'total')::NUMERIC<>v.total_amount THEN
        RAISE EXCEPTION 'Invoice has no matching immutable command outcome' USING ERRCODE='23514';
    END IF;
    IF v.subtotal_amount<>(SELECT COALESCE(sum(amount_amount),0) FROM invoice_line_items WHERE invoice_id=v.id)
       OR v.tax_total_amount<>(SELECT COALESCE(sum(tax_amount),0) FROM invoice_line_items WHERE invoice_id=v.id)
       OR NOT EXISTS (SELECT 1 FROM invoice_line_items WHERE invoice_id=v.id) THEN
        RAISE EXCEPTION 'Invoice line/header reconciliation failed' USING ERRCODE='23514';
    END IF;
    IF NOT (v.tax_decision ?& ARRAY['requirement','reasonCode','reason','sourceReference'])
       OR v.tax_decision->>'requirement'<>v.tax_requirement
       OR COALESCE(length(btrim(v.tax_decision->>'reasonCode')),0)=0
       OR COALESCE(length(btrim(v.tax_decision->>'reason')),0)=0
       OR COALESCE(length(btrim(v.tax_decision->>'sourceReference')),0)=0 THEN
        RAISE EXCEPTION 'Accounting tax requirement decision audit is missing' USING ERRCODE='23514';
    END IF;
    IF v.tax_assessment_id IS NOT NULL THEN
        SELECT * INTO a FROM invoice_tax_assessments WHERE id=v.tax_assessment_id;
        IF NOT FOUND OR a.load_id<>v.load_id OR a.customer_id<>v.customer_id
           OR a.currency<>v.subtotal_currency OR a.tax_amount<>v.tax_total_amount
           OR a.taxable_basis>v.subtotal_amount THEN
            RAISE EXCEPTION 'Invoice tax assessment context mismatch' USING ERRCODE='23514';
        END IF;
    END IF;
    IF v.invoice_purpose='PRIMARY' AND v.subtotal_amount<>(SELECT subtotal FROM accepted_rating_snapshots WHERE id=v.rating_snapshot_id) THEN
        RAISE EXCEPTION 'Primary must use accepted rating subtotal' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_rated_invoice_reconcile AFTER INSERT OR UPDATE ON invoices
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconcile_rated_invoice();
CREATE CONSTRAINT TRIGGER trg_rated_invoice_line_reconcile AFTER INSERT OR UPDATE OR DELETE ON invoice_line_items
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconcile_rated_invoice();
