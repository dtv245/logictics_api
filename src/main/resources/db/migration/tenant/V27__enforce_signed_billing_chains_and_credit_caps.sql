-- V1-V26 remain unchanged. Explicit generated purposes, never inferred legacy labels.
DROP INDEX ix_invoices_load_id;
CREATE UNIQUE INDEX ux_legacy_invoice_load ON invoices(load_id) WHERE invoice_purpose IS NULL;
CREATE INDEX ix_invoices_load_documents ON invoices(load_id,subtotal_currency,invoice_purpose);
CREATE UNIQUE INDEX ux_invoice_one_rebill ON invoices(parent_invoice_id) WHERE invoice_purpose='REBILL';
CREATE TABLE invoice_charge_claims (
    charge_id UUID PRIMARY KEY REFERENCES accessorial_charges(id) ON DELETE RESTRICT,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT
);
-- These V26 rows already have explicit generated component/source identities, not guessed history.
INSERT INTO invoice_charge_claims(charge_id,invoice_id)
SELECT l.rating_source_id,l.invoice_id FROM invoice_line_items l JOIN invoices i ON i.id=l.invoice_id
WHERE i.invoice_purpose='PRIMARY' AND l.type='ACCESSORIAL';
CREATE TABLE invoice_rebill_credit_evidence (
    rebill_invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    credit_invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    PRIMARY KEY(rebill_invoice_id,credit_invoice_id)
);
CREATE TRIGGER trg_rebill_credit_evidence_immutable BEFORE UPDATE OR DELETE ON invoice_rebill_credit_evidence
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_billing_command();

CREATE FUNCTION guard_billing_document_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p invoices; r invoices;
BEGIN
    IF NEW.load_id IS NOT NULL THEN
        PERFORM id FROM loads WHERE id=NEW.load_id FOR UPDATE;
        IF NEW.invoice_purpose IS NULL AND EXISTS(SELECT 1 FROM invoices WHERE load_id=NEW.load_id AND invoice_purpose IS NOT NULL AND id<>NEW.id)
           OR NEW.invoice_purpose IS NOT NULL AND EXISTS(SELECT 1 FROM invoices WHERE load_id=NEW.load_id AND invoice_purpose IS NULL AND id<>NEW.id) THEN
            RAISE EXCEPTION 'Unclassified legacy billing cannot be silently combined with rated billing' USING ERRCODE='23514';
        END IF;
    END IF;
    IF NEW.invoice_purpose IS NULL THEN RETURN NEW; END IF;
    IF NEW.invoice_purpose<>'PRIMARY' THEN
        SELECT * INTO p FROM invoices WHERE id=NEW.parent_invoice_id FOR UPDATE;
        SELECT * INTO r FROM invoices WHERE id=NEW.billing_chain_id;
        IF p.id IS NULL OR r.id IS NULL OR r.invoice_purpose<>'PRIMARY'
           OR p.economic_sign<>1 OR p.status NOT IN ('ISSUED','SENT','PARTIALLY_PAID','PAID')
           OR ROW(p.load_id,p.customer_id,p.subtotal_currency,p.billing_chain_id)
             IS DISTINCT FROM ROW(NEW.load_id,NEW.customer_id,NEW.subtotal_currency,NEW.billing_chain_id)
           OR NEW.invoice_purpose='SUPPLEMENTAL' AND p.invoice_purpose<>'PRIMARY' THEN
            RAISE EXCEPTION 'Invalid correction parent/billing chain identity' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_billing_document_identity BEFORE INSERT ON invoices
    FOR EACH ROW EXECUTE FUNCTION guard_billing_document_identity();

CREATE OR REPLACE FUNCTION guard_rated_invoice_line() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v invoices; original invoice_line_items; reserved_amount NUMERIC; reserved_tax NUMERIC; reserved_quantity NUMERIC;
BEGIN
    IF TG_OP IN ('UPDATE','DELETE') THEN
        SELECT * INTO v FROM invoices WHERE id=OLD.invoice_id FOR UPDATE;
        IF v.invoice_purpose IS NOT NULL AND v.status<>'DRAFT' THEN
            RAISE EXCEPTION 'Issued invoice lines cannot be edited, deleted or moved' USING ERRCODE='23514';
        END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    SELECT * INTO v FROM invoices WHERE id=NEW.invoice_id FOR UPDATE;
    IF v.invoice_purpose IS NULL THEN RETURN NEW; END IF;
    IF v.status<>'DRAFT' THEN RAISE EXCEPTION 'Cannot attach lines to issued invoice' USING ERRCODE='23514'; END IF;
    IF NEW.amount_amount<0 OR NEW.tax_amount<0 OR NEW.rating_source_id IS NULL
       OR NEW.amount_currency<>v.subtotal_currency OR NEW.quantity<=0
       OR NEW.amount_amount IN ('NaN','Infinity','-Infinity') OR NEW.tax_amount IN ('NaN','Infinity','-Infinity') THEN
        RAISE EXCEPTION 'Invalid rated invoice financial line' USING ERRCODE='23514';
    END IF;
    IF v.invoice_purpose='SUPPLEMENTAL' AND (NEW.type<>'ACCESSORIAL' OR NEW.amount_amount<=0) THEN
        RAISE EXCEPTION 'Supplemental must be approved positive incremental charge, not base freight/FSC' USING ERRCODE='23514';
    END IF;
    IF v.invoice_purpose='CREDIT' THEN
        SELECT * INTO original FROM invoice_line_items WHERE id=NEW.credited_line_id FOR UPDATE;
        IF original.id IS NULL OR original.invoice_id<>v.parent_invoice_id OR NEW.rating_source_id<>original.id
           OR NEW.type<>'CREDIT' OR NEW.amount_amount<=0
           OR NEW.credited_quantity IS NOT NULL AND (NEW.credited_quantity<=0 OR NEW.credited_quantity IN ('NaN','Infinity','-Infinity')) THEN
            RAISE EXCEPTION 'Credit requires explicit original parent line/positive amount/quantity evidence' USING ERRCODE='23514';
        END IF;
        SELECT COALESCE(sum(amount_amount),0),COALESCE(sum(tax_amount),0),COALESCE(sum(credited_quantity),0)
        INTO reserved_amount,reserved_tax,reserved_quantity FROM invoice_line_items
        WHERE credited_line_id=original.id AND id<>NEW.id;
        IF reserved_amount+NEW.amount_amount>original.amount_amount OR reserved_tax+NEW.tax_amount>original.tax_amount
           OR NEW.credited_quantity IS NOT NULL AND reserved_quantity+NEW.credited_quantity>original.quantity THEN
            RAISE EXCEPTION 'Cumulative reserved/issued credit exceeds original line amount/tax/quantity' USING ERRCODE='23514';
        END IF;
    ELSIF NEW.credited_line_id IS NOT NULL OR NEW.credited_quantity IS NOT NULL THEN
        RAISE EXCEPTION 'Only CREDIT may carry credited line evidence' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION guard_invoice_charge_claim() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v invoices;
BEGIN
    SELECT * INTO v FROM invoices WHERE id=CASE WHEN TG_OP='DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END FOR UPDATE;
    IF TG_OP='UPDATE' OR v.status<>'DRAFT' THEN
        RAISE EXCEPTION 'Issued charge claim is immutable' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    IF v.invoice_purpose NOT IN ('PRIMARY','SUPPLEMENTAL','REBILL') OR NOT EXISTS(
        SELECT 1 FROM invoice_line_items WHERE invoice_id=v.id AND type='ACCESSORIAL' AND rating_source_id=NEW.charge_id) THEN
        RAISE EXCEPTION 'Charge claim requires explicit generated financial source line' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_invoice_charge_claim BEFORE INSERT OR UPDATE OR DELETE ON invoice_charge_claims
    FOR EACH ROW EXECUTE FUNCTION guard_invoice_charge_claim();

CREATE FUNCTION reconcile_billing_chain() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v invoices; p invoices; c invoice_billing_commands; s accepted_rating_snapshots;
        credit_subtotal NUMERIC; credit_tax NUMERIC; valid_count INTEGER; evidence_count INTEGER;
BEGIN
    IF TG_TABLE_NAME='invoice_rebill_credit_evidence' THEN
        SELECT * INTO v FROM invoices WHERE id=NEW.rebill_invoice_id;
    ELSE SELECT * INTO v FROM invoices WHERE id=NEW.id; END IF;
    IF v.id IS NULL OR v.invoice_purpose IS NULL THEN RETURN NULL; END IF;
    SELECT * INTO c FROM invoice_billing_commands WHERE id=v.billing_command_id;
    IF (c.outcome->>'purpose') IS DISTINCT FROM v.invoice_purpose
       OR (c.outcome->>'economicSign')::INTEGER IS DISTINCT FROM v.economic_sign
       OR (c.outcome->>'snapshotId')::UUID IS DISTINCT FROM v.rating_snapshot_id
       OR (c.outcome->>'parentInvoiceId')::UUID IS DISTINCT FROM v.parent_invoice_id
       OR (c.outcome->>'billingChainId')::UUID IS DISTINCT FROM v.billing_chain_id THEN
        RAISE EXCEPTION 'Command financial identity does not match invoice' USING ERRCODE='23514';
    END IF;
    IF v.invoice_purpose<>'CREDIT' THEN
        SELECT * INTO s FROM accepted_rating_snapshots WHERE id=v.rating_snapshot_id;
        IF v.invoice_purpose IN ('PRIMARY','REBILL') AND (
           v.subtotal_amount<>s.subtotal OR
           (SELECT count(*) FROM invoice_line_items WHERE invoice_id=v.id)<>jsonb_array_length(s.calculation->'lines')) THEN
            RAISE EXCEPTION 'Primary/rebill must preserve complete accepted rating lines' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM invoice_line_items l WHERE l.invoice_id=v.id AND NOT EXISTS(
           SELECT 1 FROM jsonb_array_elements(s.calculation->'lines') x WHERE x->>'componentType'=l.type
             AND (x->>'sourceId')::UUID=l.rating_source_id AND (x->>'amount')::NUMERIC=l.amount_amount)) THEN
            RAISE EXCEPTION 'Invoice line does not match accepted source/amount' USING ERRCODE='23514';
        END IF;
        IF EXISTS(SELECT 1 FROM invoice_line_items l WHERE l.invoice_id=v.id AND l.type='ACCESSORIAL' AND NOT EXISTS(
           SELECT 1 FROM invoice_charge_claims q WHERE q.charge_id=l.rating_source_id AND (
             q.invoice_id=v.id OR v.invoice_purpose='REBILL' AND EXISTS(
               SELECT 1 FROM invoice_line_items original WHERE original.invoice_id=v.parent_invoice_id
                 AND original.type='ACCESSORIAL' AND original.rating_source_id=q.charge_id
                 AND EXISTS(SELECT 1 FROM invoices claim_owner WHERE claim_owner.id=q.invoice_id
                   AND claim_owner.billing_chain_id=v.billing_chain_id))))) THEN
            RAISE EXCEPTION 'Approved charge lacks safe billing claim' USING ERRCODE='23514';
        END IF;
    END IF;
    IF v.invoice_purpose='REBILL' THEN
        SELECT * INTO p FROM invoices WHERE id=v.parent_invoice_id;
        SELECT count(*) INTO evidence_count FROM invoice_rebill_credit_evidence WHERE rebill_invoice_id=v.id;
        SELECT count(*),COALESCE(sum(credit.subtotal_amount),0),COALESCE(sum(credit.tax_total_amount),0)
        INTO valid_count,credit_subtotal,credit_tax FROM invoice_rebill_credit_evidence e JOIN invoices credit ON credit.id=e.credit_invoice_id
        WHERE e.rebill_invoice_id=v.id AND credit.invoice_purpose='CREDIT' AND credit.parent_invoice_id=p.id
          AND credit.billing_chain_id=v.billing_chain_id AND credit.status IN ('ISSUED','SENT','PARTIALLY_PAID','PAID');
        IF evidence_count=0 OR valid_count<>evidence_count OR credit_subtotal<>p.subtotal_amount OR credit_tax<>p.tax_total_amount
           OR v.rating_snapshot_id=p.rating_snapshot_id THEN
            RAISE EXCEPTION 'Rebill requires explicit exact full economic credit evidence and new accepted rating' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER trg_billing_chain_reconcile AFTER INSERT OR UPDATE ON invoices
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconcile_billing_chain();
CREATE CONSTRAINT TRIGGER trg_rebill_credit_evidence_reconcile AFTER INSERT ON invoice_rebill_credit_evidence
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reconcile_billing_chain();
