ALTER TABLE payroll_run_items
    ADD COLUMN no_payment_required_at TIMESTAMPTZ,
    ADD COLUMN no_payment_required_by UUID REFERENCES employees(id) ON DELETE RESTRICT,
    ADD COLUMN no_payment_reason_code VARCHAR(40),
    ADD COLUMN no_payment_reason VARCHAR(1000);

ALTER TABLE payroll_run_items DROP CONSTRAINT chk_payroll_items_status;
ALTER TABLE payroll_run_items ADD CONSTRAINT chk_payroll_items_status
    CHECK (status IN ('CALCULATED','VALIDATION_REQUIRED','SCHEDULED','PAYMENT_PENDING','PAYMENT_FAILED','PAID','NO_PAYMENT_REQUIRED'));
ALTER TABLE payroll_run_items ADD CONSTRAINT chk_payroll_no_payment_audit
    CHECK ((status = 'NO_PAYMENT_REQUIRED'
            AND no_payment_required_at IS NOT NULL
            AND no_payment_required_by IS NOT NULL
            AND no_payment_reason_code IS NOT NULL
            AND no_payment_reason_code IN ('ZERO_NET_PAY','FULLY_OFFSET_BY_DEDUCTIONS','MANUAL_ADJUSTMENT_ZERO_BALANCE')
            AND no_payment_reason IS NOT NULL AND length(btrim(no_payment_reason)) > 0
            AND net_amount IS NOT NULL
            AND net_amount = 0)
        OR (status <> 'NO_PAYMENT_REQUIRED'
            AND no_payment_required_at IS NULL AND no_payment_required_by IS NULL
            AND no_payment_reason_code IS NULL AND no_payment_reason IS NULL));

ALTER TABLE payroll_runs
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN completed_by UUID REFERENCES employees(id) ON DELETE RESTRICT,
    ADD COLUMN completion_source VARCHAR(40);
ALTER TABLE payroll_runs DROP CONSTRAINT chk_payroll_runs_status;
ALTER TABLE payroll_runs ADD CONSTRAINT chk_payroll_completion_audit
    CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL AND completion_source IS NOT NULL
            AND completion_source IN ('PAYMENT_PROVIDER','BANK_RECONCILIATION','NO_PAYMENT_DISPOSITION','LEGACY_PAID_RUN'))
        OR (status <> 'COMPLETED' AND completed_at IS NULL AND completed_by IS NULL AND completion_source IS NULL));

CREATE OR REPLACE FUNCTION protect_locked_payroll_run() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.status IN ('LOCKED','PAYMENT_SCHEDULED','COMPLETED') THEN
        RAISE EXCEPTION 'Locked or completed payroll cannot be deleted';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status IN ('LOCKED','PAYMENT_SCHEDULED','COMPLETED') THEN
        IF (to_jsonb(OLD) - ARRAY['status','paid_at','completed_at','completed_by','completion_source','version'])
           IS DISTINCT FROM
           (to_jsonb(NEW) - ARRAY['status','paid_at','completed_at','completed_by','completion_source','version']) THEN
            RAISE EXCEPTION 'Locked payroll financial inputs and audit are immutable';
        END IF;
        IF ROW(OLD.completed_at, OLD.completed_by, OLD.completion_source)
           IS DISTINCT FROM ROW(NEW.completed_at, NEW.completed_by, NEW.completion_source)
           AND NOT (OLD.status <> 'COMPLETED' AND NEW.status = 'COMPLETED'
                    AND NEW.completed_at IS NOT NULL AND NEW.completion_source IS NOT NULL) THEN
            RAISE EXCEPTION 'Payroll completion audit can only be written with terminal completion';
        END IF;
        IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
            (OLD.status = 'LOCKED' AND NEW.status = 'PAYMENT_SCHEDULED')
            OR (OLD.status IN ('LOCKED','PAYMENT_SCHEDULED') AND NEW.status = 'COMPLETED')
        ) THEN
            RAISE EXCEPTION 'Payroll cannot be reopened, skipped, or changed after completion';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.status = 'COMPLETED' AND OLD.status <> 'COMPLETED'
       AND EXISTS (SELECT 1 FROM payroll_run_items
                    WHERE payroll_run_id = NEW.id
                      AND status NOT IN ('PAID','NO_PAYMENT_REQUIRED')) THEN
        RAISE EXCEPTION 'Payroll run can complete only when every item is paid or requires no payment';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.status = 'COMPLETED' AND OLD.status NOT IN ('COMPLETED','PAID')
       AND OLD.status NOT IN ('LOCKED','PAYMENT_SCHEDULED') THEN
        RAISE EXCEPTION 'Only a locked or payment-scheduled payroll run can complete';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION protect_locked_payroll_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_status VARCHAR;
    valid_no_payment_transition BOOLEAN;
BEGIN
    valid_no_payment_transition := TG_OP = 'UPDATE'
        AND OLD.status IN ('CALCULATED','SCHEDULED')
        AND NEW.status = 'NO_PAYMENT_REQUIRED'
        AND NEW.net_amount = 0
        AND NEW.no_payment_required_at IS NOT NULL
        AND NEW.no_payment_required_by IS NOT NULL
        AND NEW.no_payment_reason_code IN ('ZERO_NET_PAY','FULLY_OFFSET_BY_DEDUCTIONS','MANUAL_ADJUSTMENT_ZERO_BALANCE')
        AND NEW.no_payment_reason IS NOT NULL AND length(btrim(NEW.no_payment_reason)) > 0
        AND NOT EXISTS (SELECT 1 FROM payroll_payments p WHERE p.payroll_run_item_id = OLD.id);

    IF TG_OP IN ('UPDATE','DELETE') THEN
        SELECT status INTO parent_status FROM payroll_runs WHERE id = OLD.payroll_run_id FOR UPDATE;
        IF parent_status IN ('LOCKED','PAYMENT_SCHEDULED','COMPLETED') THEN
            IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Locked payroll item cannot be deleted'; END IF;
            IF (to_jsonb(OLD) - ARRAY['status','version','no_payment_required_at','no_payment_required_by','no_payment_reason_code','no_payment_reason'])
               IS DISTINCT FROM
               (to_jsonb(NEW) - ARRAY['status','version','no_payment_required_at','no_payment_required_by','no_payment_reason_code','no_payment_reason']) THEN
                RAISE EXCEPTION 'Locked payroll item money and snapshot are immutable';
            END IF;
            IF ROW(OLD.no_payment_required_at,OLD.no_payment_required_by,OLD.no_payment_reason_code,OLD.no_payment_reason)
               IS DISTINCT FROM ROW(NEW.no_payment_required_at,NEW.no_payment_required_by,NEW.no_payment_reason_code,NEW.no_payment_reason)
               AND NOT valid_no_payment_transition THEN
                RAISE EXCEPTION 'No-payment audit is immutable after disposition';
            END IF;
            IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
                (OLD.status IN ('CALCULATED','SCHEDULED','PAYMENT_FAILED') AND NEW.status = 'PAYMENT_PENDING')
                OR (OLD.status = 'PAYMENT_PENDING' AND NEW.status IN ('PAID','PAYMENT_FAILED'))
                OR valid_no_payment_transition
                OR (OLD.status = 'PAYMENT_FAILED' AND NEW.status = 'PAID' AND EXISTS (
                    SELECT 1 FROM payroll_payments p
                    JOIN payroll_payment_events e ON e.payment_id = p.id
                    WHERE p.payroll_run_item_id = OLD.id AND p.status = 'SUCCEEDED'
                      AND e.source_type = 'BANK' AND e.status = 'APPLIED' AND e.outcome = 'SUCCEEDED'
                      AND e.resolves_event_id IS NOT NULL AND e.amount = p.amount
                      AND e.currency = p.currency AND e.provider_reference = p.provider_reference
                ))
            ) THEN RAISE EXCEPTION 'Payroll item payment workflow invalid'; END IF;
        END IF;
    END IF;

    IF NEW.status = 'NO_PAYMENT_REQUIRED' AND NOT valid_no_payment_transition
       AND (TG_OP = 'INSERT' OR OLD.status <> 'NO_PAYMENT_REQUIRED') THEN
        RAISE EXCEPTION 'No-payment disposition requires zero net, actor, reason and no payment attempts';
    END IF;
    IF TG_OP IN ('INSERT','UPDATE') THEN
        SELECT status INTO parent_status FROM payroll_runs WHERE id = NEW.payroll_run_id FOR UPDATE;
        IF parent_status IN ('LOCKED','PAYMENT_SCHEDULED','COMPLETED')
           AND (TG_OP = 'INSERT' OR NEW.payroll_run_id <> OLD.payroll_run_id) THEN
            RAISE EXCEPTION 'Cannot attach item to locked payroll';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

-- Preserve the historical payout fact while adopting distinct run-completion semantics.
UPDATE payroll_runs
   SET status = 'COMPLETED',
       completed_at = COALESCE(paid_at, CURRENT_TIMESTAMP),
       completion_source = 'LEGACY_PAID_RUN'
 WHERE status = 'PAID';

ALTER TABLE payroll_runs ADD CONSTRAINT chk_payroll_runs_status
    CHECK (status IN ('DRAFT','CALCULATED','VALIDATION_REQUIRED','IN_REVIEW','APPROVED','LOCKED','PAYMENT_SCHEDULED','COMPLETED','CANCELLED'));
