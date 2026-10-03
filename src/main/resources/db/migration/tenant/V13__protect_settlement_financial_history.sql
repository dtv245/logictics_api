-- Forward-only correction; V10/V11 financial schema is already applied.
ALTER TABLE settlements ADD COLUMN request_key VARCHAR(120);
ALTER TABLE settlements ADD COLUMN validation_reason VARCHAR(1000);
ALTER TABLE settlements ADD CONSTRAINT chk_settlement_workflow_status
    CHECK (status IN ('DRAFT','CALCULATED','VALIDATION_REQUIRED','IN_REVIEW','APPROVED','LOCKED','PAYMENT_SCHEDULED','PAID','CANCELLED')) NOT VALID;
ALTER TABLE settlement_lines ADD COLUMN business_date DATE;
-- A trip-attributed line must stay a trip cost until an explicit load allocation exists.
ALTER TABLE shipment_costs ALTER COLUMN load_id DROP NOT NULL;
ALTER TABLE shipment_costs ADD CONSTRAINT chk_shipment_cost_attribution CHECK (load_id IS NOT NULL OR trip_id IS NOT NULL);
CREATE UNIQUE INDEX uq_settlements_parent_request ON settlements(parent_settlement_id, request_key)
    WHERE parent_settlement_id IS NOT NULL AND request_key IS NOT NULL;
CREATE UNIQUE INDEX uq_settlements_one_reversal ON settlements(parent_settlement_id)
    WHERE settlement_type = 'REVERSAL';
-- Do not relabel/backfill potentially inconsistent historical totals. New writes must reconcile.
ALTER TABLE settlements ADD CONSTRAINT chk_settlements_net
    CHECK (settlement_net = gross_earnings - deduction_amount + reimbursement_amount) NOT VALID;
ALTER TABLE settlement_lines ADD CONSTRAINT chk_settlement_lines_nonnegative CHECK (amount >= 0) NOT VALID;

CREATE FUNCTION protect_locked_settlement() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') THEN
        IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Locked settlement cannot be deleted'; END IF;
        IF ROW(OLD.driver_id,OLD.pay_period_id,OLD.settlement_type,OLD.parent_settlement_id,OLD.sequence_number,
               OLD.pay_policy_id,OLD.pay_policy_version,OLD.currency,OLD.mileage_pay,OLD.load_pay,OLD.percentage_pay,
               OLD.hourly_pay,OLD.accessorial_pay,OLD.bonus_amount,OLD.reimbursement_amount,OLD.deduction_amount,
               OLD.gross_earnings,OLD.settlement_net,OLD.eligible_miles,OLD.loaded_miles,OLD.empty_miles,OLD.eligible_hours,
               OLD.calculation_snapshot_id,OLD.request_key,OLD.locked_at,OLD.locked_by,OLD.validation_reason,
               OLD.settlement_number,OLD.calculated_at,OLD.reviewed_at,OLD.reviewed_by,OLD.approved_at,OLD.approved_by)
          IS DISTINCT FROM ROW(NEW.driver_id,NEW.pay_period_id,NEW.settlement_type,NEW.parent_settlement_id,NEW.sequence_number,
               NEW.pay_policy_id,NEW.pay_policy_version,NEW.currency,NEW.mileage_pay,NEW.load_pay,NEW.percentage_pay,
               NEW.hourly_pay,NEW.accessorial_pay,NEW.bonus_amount,NEW.reimbursement_amount,NEW.deduction_amount,
               NEW.gross_earnings,NEW.settlement_net,NEW.eligible_miles,NEW.loaded_miles,NEW.empty_miles,NEW.eligible_hours,
               NEW.calculation_snapshot_id,NEW.request_key,NEW.locked_at,NEW.locked_by,NEW.validation_reason,
               NEW.settlement_number,NEW.calculated_at,NEW.reviewed_at,NEW.reviewed_by,NEW.approved_at,NEW.approved_by)
        THEN RAISE EXCEPTION 'Locked settlement financial inputs are immutable'; END IF;
        IF NEW.status <> OLD.status AND NOT
            ((OLD.status='LOCKED' AND NEW.status='PAYMENT_SCHEDULED') OR (OLD.status='PAYMENT_SCHEDULED' AND NEW.status='PAID'))
        THEN RAISE EXCEPTION 'Locked settlement cannot be unlocked or skip payment workflow'; END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_locked_settlement BEFORE UPDATE OR DELETE ON settlements
    FOR EACH ROW EXECUTE FUNCTION protect_locked_settlement();

CREATE FUNCTION protect_locked_settlement_line() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE locked_parent boolean;
BEGIN
    IF TG_OP IN ('UPDATE','DELETE') THEN
        SELECT status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') INTO locked_parent FROM settlements WHERE id=OLD.settlement_id FOR UPDATE;
        IF locked_parent THEN RAISE EXCEPTION 'Locked settlement lines are immutable'; END IF;
    END IF;
    IF TG_OP IN ('INSERT','UPDATE') THEN
        SELECT status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') INTO locked_parent FROM settlements WHERE id=NEW.settlement_id FOR UPDATE;
        IF locked_parent THEN RAISE EXCEPTION 'Cannot attach lines to locked settlement'; END IF;
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_locked_settlement_line BEFORE INSERT OR UPDATE OR DELETE ON settlement_lines
    FOR EACH ROW EXECUTE FUNCTION protect_locked_settlement_line();

CREATE FUNCTION protect_used_driver_pay_policy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM settlements WHERE pay_policy_id=OLD.id) THEN
        RAISE EXCEPTION 'Referenced driver pay policy version is immutable; append a new version';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_used_driver_pay_policy BEFORE UPDATE OR DELETE ON driver_pay_policies
    FOR EACH ROW EXECUTE FUNCTION protect_used_driver_pay_policy();
