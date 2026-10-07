ALTER TABLE payroll_payments ADD COLUMN provider_key VARCHAR(100);
ALTER TABLE payroll_payments ADD COLUMN destination_reference VARCHAR(200);
ALTER TABLE payroll_payments ADD COLUMN request_snapshot_json JSONB;
ALTER TABLE payroll_payments ADD COLUMN dispatch_started_at TIMESTAMPTZ;
ALTER TABLE payroll_payments ADD COLUMN submitted_at TIMESTAMPTZ;
ALTER TABLE payroll_payments ADD COLUMN scheduled_by UUID REFERENCES employees(id) ON DELETE RESTRICT;
ALTER TABLE payroll_payments ADD COLUMN reconciled_by UUID REFERENCES employees(id) ON DELETE RESTRICT;
ALTER TABLE payroll_payments ADD COLUMN reconciliation_reference VARCHAR(200);
ALTER TABLE payroll_payments DROP CONSTRAINT chk_payroll_payment_status;
ALTER TABLE payroll_payments ADD CONSTRAINT chk_payroll_payment_status CHECK(status IN ('SCHEDULED','SUBMITTED','PROCESSING','SUCCEEDED','FAILED','CANCELLED','RECONCILIATION_REQUIRED'));
CREATE UNIQUE INDEX uq_payroll_item_active_payment ON payroll_payments(payroll_run_item_id) WHERE status IN ('SCHEDULED','SUBMITTED','PROCESSING','RECONCILIATION_REQUIRED');
CREATE FUNCTION protect_payroll_payment_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Payroll payment attempts are immutable history'; END IF;
 IF ROW(OLD.id,OLD.payroll_run_item_id,OLD.attempt_number,OLD.idempotency_key,OLD.payment_method,OLD.amount,OLD.currency,
  OLD.provider_key,OLD.destination_reference,OLD.request_snapshot_json,OLD.scheduled_at,OLD.scheduled_by,OLD.created_at)
  IS DISTINCT FROM ROW(NEW.id,NEW.payroll_run_item_id,NEW.attempt_number,NEW.idempotency_key,NEW.payment_method,NEW.amount,NEW.currency,
  NEW.provider_key,NEW.destination_reference,NEW.request_snapshot_json,NEW.scheduled_at,NEW.scheduled_by,NEW.created_at)
  THEN RAISE EXCEPTION 'Payroll payment financial inputs are immutable'; END IF;
 IF OLD.provider_reference IS NOT NULL AND OLD.provider_reference IS DISTINCT FROM NEW.provider_reference
  THEN RAISE EXCEPTION 'Payroll payment provider reference cannot change'; END IF;
 IF NEW.status<>OLD.status AND NOT (
  (OLD.status='SCHEDULED' AND NEW.status IN ('PROCESSING','SUCCEEDED','FAILED','CANCELLED'))
  OR (OLD.status IN ('SUBMITTED','PROCESSING') AND NEW.status IN ('SUCCEEDED','FAILED','RECONCILIATION_REQUIRED'))
  OR (OLD.status='FAILED' AND NEW.status='RECONCILIATION_REQUIRED'))
  THEN RAISE EXCEPTION 'Payroll payment workflow invalid'; END IF;
 IF OLD.status='SUCCEEDED' AND (to_jsonb(OLD) IS DISTINCT FROM to_jsonb(NEW))
  THEN RAISE EXCEPTION 'Successful payment evidence is immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_payroll_payment_attempt BEFORE UPDATE OR DELETE ON payroll_payments FOR EACH ROW EXECUTE FUNCTION protect_payroll_payment_attempt();
