-- Append-only billing impact audit. No rewrite of V1-V27 or finalized financial history.
CREATE TABLE settlement_revenue_commands (
    id UUID PRIMARY KEY,
    operation VARCHAR(40) NOT NULL CHECK(operation IN ('REVENUE_RECALCULATE','REVENUE_ADJUSTMENT')),
    request_key VARCHAR(120) NOT NULL CHECK(length(trim(request_key))>0),
    input_hash VARCHAR(64) NOT NULL CHECK(input_hash~'^[a-f0-9]{64}$'),
    input JSONB NOT NULL,
    outcome JSONB NOT NULL,
    settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE RESTRICT,
    actor UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    captured_at TIMESTAMPTZ NOT NULL,
    UNIQUE(operation,request_key)
);
CREATE TABLE settlement_billing_adjustments (
    original_settlement_id UUID NOT NULL REFERENCES settlements(id) ON DELETE RESTRICT,
    affected_document_id UUID NOT NULL REFERENCES invoices(id) ON DELETE RESTRICT,
    original_line_id UUID NOT NULL REFERENCES settlement_lines(id) ON DELETE RESTRICT,
    adjustment_settlement_id UUID REFERENCES settlements(id) ON DELETE RESTRICT,
    command_id UUID NOT NULL UNIQUE REFERENCES settlement_revenue_commands(id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    policy_id UUID NOT NULL REFERENCES driver_pay_policies(id) ON DELETE RESTRICT,
    policy_version INTEGER NOT NULL,
    earning_date DATE NOT NULL,
    revenue_basis VARCHAR(40) NOT NULL CHECK(revenue_basis IN ('PRIMARY_INVOICE_REVENUE','NET_ELIGIBLE_REVENUE','INVOICE_SUBTOTAL')),
    economic_delta NUMERIC NOT NULL CHECK(economic_delta NOT IN ('NaN','Infinity','-Infinity')),
    pay_delta NUMERIC NOT NULL CHECK(pay_delta NOT IN ('NaN','Infinity','-Infinity')),
    previous_revenue NUMERIC NOT NULL,
    resulting_revenue NUMERIC NOT NULL CHECK(resulting_revenue>=0),
    reason_code VARCHAR(80) NOT NULL CHECK(length(trim(reason_code))>0),
    reason VARCHAR(300) NOT NULL CHECK(length(trim(reason))>0),
    actor UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    captured_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(original_settlement_id,affected_document_id),
    CHECK((pay_delta=0 AND adjustment_settlement_id IS NULL) OR (pay_delta<>0 AND adjustment_settlement_id IS NOT NULL)),
    CHECK(resulting_revenue=previous_revenue+economic_delta)
);
CREATE TRIGGER trg_settlement_revenue_command_immutable BEFORE UPDATE OR DELETE ON settlement_revenue_commands
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_billing_command();
CREATE TRIGGER trg_settlement_billing_adjustment_immutable BEFORE UPDATE OR DELETE ON settlement_billing_adjustments
    FOR EACH ROW EXECUTE FUNCTION protect_invoice_billing_command();
CREATE FUNCTION guard_settlement_billing_adjustment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p settlements; d invoices; l settlement_lines; child settlements;
BEGIN
    SELECT * INTO p FROM settlements WHERE id=NEW.original_settlement_id FOR UPDATE;
    SELECT * INTO d FROM invoices WHERE id=NEW.affected_document_id;
    SELECT * INTO l FROM settlement_lines WHERE id=NEW.original_line_id;
    IF p.settlement_type<>'ORIGINAL' OR p.status NOT IN ('LOCKED','PAYMENT_SCHEDULED','PAID')
       OR l.settlement_id<>p.id OR l.line_type<>'REVENUE_PERCENT' OR l.load_id<>d.load_id
       OR d.invoice_purpose IS NULL OR d.status NOT IN ('ISSUED','SENT','PARTIALLY_PAID','PAID')
       OR l.business_date IS DISTINCT FROM NEW.earning_date OR p.currency<>d.subtotal_currency THEN
        RAISE EXCEPTION 'Invalid frozen settlement/billing correction identity' USING ERRCODE='23514';
    END IF;
    IF NEW.adjustment_settlement_id IS NOT NULL THEN
        SELECT * INTO child FROM settlements WHERE id=NEW.adjustment_settlement_id;
        IF child.parent_settlement_id IS DISTINCT FROM p.id OR child.settlement_type<>'ADJUSTMENT'
           OR child.currency<>p.currency OR child.settlement_net<>NEW.pay_delta THEN
            RAISE EXCEPTION 'Billing pay delta must reference exact append-only adjustment' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_settlement_billing_adjustment BEFORE INSERT ON settlement_billing_adjustments
    FOR EACH ROW EXECUTE FUNCTION guard_settlement_billing_adjustment();
