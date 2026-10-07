CREATE TABLE payroll_payment_events (
 id UUID PRIMARY KEY, payment_id UUID NOT NULL REFERENCES payroll_payments(id) ON DELETE RESTRICT,
 source_type VARCHAR(30) NOT NULL CHECK(source_type IN ('PROVIDER','BANK')),
 source_key VARCHAR(200) NOT NULL, provider_key VARCHAR(100), actor_id UUID REFERENCES employees(id) ON DELETE RESTRICT,
 outcome VARCHAR(30) NOT NULL CHECK(outcome IN ('SUCCEEDED','FAILED')),
 amount NUMERIC(19,4) NOT NULL CHECK(amount>=0), currency VARCHAR(3) NOT NULL, provider_reference VARCHAR(200),
 occurred_at TIMESTAMPTZ NOT NULL, status VARCHAR(40) NOT NULL CHECK(status IN ('APPLIED','DUPLICATE','REJECTED','RECONCILIATION_REQUIRED')),
 reason VARCHAR(300), payload_json JSONB NOT NULL, verification_json JSONB NOT NULL,
 resolves_event_id UUID REFERENCES payroll_payment_events(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE NULLS NOT DISTINCT(source_type,provider_key,source_key),
 CHECK((source_type='PROVIDER' AND provider_key IS NOT NULL AND actor_id IS NULL)
    OR (source_type='BANK' AND provider_key IS NULL AND actor_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_payroll_event_resolution ON payroll_payment_events(resolves_event_id)
 WHERE resolves_event_id IS NOT NULL AND status='APPLIED';
CREATE TRIGGER guard_payroll_payment_event BEFORE UPDATE OR DELETE ON payroll_payment_events FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_version();

CREATE FUNCTION require_payroll_success_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='SUCCEEDED' AND OLD.status<>'SUCCEEDED' AND NOT EXISTS(
  SELECT 1 FROM payroll_payment_events e WHERE e.payment_id=NEW.id AND e.outcome='SUCCEEDED' AND e.status='APPLIED'
   AND e.amount=NEW.amount AND e.currency=NEW.currency AND e.provider_reference IS NOT DISTINCT FROM NEW.provider_reference)
  THEN RAISE EXCEPTION 'Payment success requires verified matching evidence'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_payroll_success_evidence BEFORE UPDATE ON payroll_payments FOR EACH ROW EXECUTE FUNCTION require_payroll_success_evidence();
CREATE FUNCTION require_paid_payroll_item_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='PAID' AND OLD.status<>'PAID' AND NOT EXISTS(
  SELECT 1 FROM payroll_payments p WHERE p.payroll_run_item_id=NEW.id AND p.status='SUCCEEDED' AND p.amount=NEW.net_amount AND p.currency=NEW.currency)
  THEN RAISE EXCEPTION 'Payroll item requires successful matching payment'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_paid_payroll_item BEFORE UPDATE ON payroll_run_items FOR EACH ROW EXECUTE FUNCTION require_paid_payroll_item_evidence();
CREATE FUNCTION require_paid_settlement_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='PAID' AND OLD.status<>'PAID' AND NOT EXISTS(
  SELECT 1 FROM payroll_run_item_settlements c JOIN payroll_payments p ON p.payroll_run_item_id=c.payroll_run_item_id
   WHERE c.settlement_id=NEW.id AND p.status='SUCCEEDED')
  THEN RAISE EXCEPTION 'Settlement requires successful payroll payment'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_paid_settlement BEFORE UPDATE ON settlements FOR EACH ROW EXECUTE FUNCTION require_paid_settlement_evidence();
