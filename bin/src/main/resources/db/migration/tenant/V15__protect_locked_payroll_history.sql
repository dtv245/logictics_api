ALTER TABLE payroll_runs ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE payroll_runs ADD COLUMN reviewed_by UUID;

CREATE FUNCTION protect_locked_payroll_run() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND NEW.status='PAID' AND EXISTS(SELECT 1 FROM payroll_run_items WHERE payroll_run_id=NEW.id AND status<>'PAID')
  THEN RAISE EXCEPTION 'Payroll is PAID only when every required item is PAID'; END IF;
 IF OLD.status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') THEN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Locked payroll cannot be deleted'; END IF;
  IF (to_jsonb(OLD)-ARRAY['status','paid_at','version']) IS DISTINCT FROM (to_jsonb(NEW)-ARRAY['status','paid_at','version'])
   THEN RAISE EXCEPTION 'Locked payroll financial inputs and audit are immutable'; END IF;
  IF NEW.status<>OLD.status AND NOT ((OLD.status='LOCKED' AND NEW.status='PAYMENT_SCHEDULED')
    OR (OLD.status='PAYMENT_SCHEDULED' AND NEW.status='PAID'))
   THEN RAISE EXCEPTION 'Payroll cannot be unlocked or skip payment workflow'; END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER guard_locked_payroll_run BEFORE UPDATE OR DELETE ON payroll_runs FOR EACH ROW EXECUTE FUNCTION protect_locked_payroll_run();

CREATE FUNCTION protect_locked_payroll_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status VARCHAR;
BEGIN
 IF TG_OP IN ('UPDATE','DELETE') THEN
  SELECT status INTO parent_status FROM payroll_runs WHERE id=OLD.payroll_run_id FOR UPDATE;
  IF parent_status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') THEN
   IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Locked payroll item cannot be deleted'; END IF;
   IF (to_jsonb(OLD)-ARRAY['status','version']) IS DISTINCT FROM (to_jsonb(NEW)-ARRAY['status','version'])
    THEN RAISE EXCEPTION 'Locked payroll item money and snapshot are immutable'; END IF;
   IF NEW.status<>OLD.status AND NOT (
      (OLD.status IN ('CALCULATED','SCHEDULED','PAYMENT_FAILED') AND NEW.status='PAYMENT_PENDING')
       OR (OLD.status='PAYMENT_PENDING' AND NEW.status IN ('PAID','PAYMENT_FAILED')))
    THEN RAISE EXCEPTION 'Payroll item payment workflow invalid'; END IF;
  END IF;
 END IF;
 IF TG_OP IN ('INSERT','UPDATE') THEN
  SELECT status INTO parent_status FROM payroll_runs WHERE id=NEW.payroll_run_id FOR UPDATE;
  IF parent_status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') AND (TG_OP='INSERT' OR NEW.payroll_run_id<>OLD.payroll_run_id)
   THEN RAISE EXCEPTION 'Cannot attach item to locked payroll'; END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER guard_locked_payroll_item BEFORE INSERT OR UPDATE OR DELETE ON payroll_run_items FOR EACH ROW EXECUTE FUNCTION protect_locked_payroll_item();

CREATE FUNCTION protect_payroll_settlement_claim() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Payroll settlement source claim is immutable'; END $$;
CREATE TRIGGER guard_payroll_settlement_claim BEFORE UPDATE OR DELETE ON payroll_run_item_settlements FOR EACH ROW EXECUTE FUNCTION protect_payroll_settlement_claim();
CREATE FUNCTION require_open_payroll_source_claim() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status VARCHAR;
BEGIN
 SELECT r.status INTO parent_status FROM payroll_runs r JOIN payroll_run_items i ON i.payroll_run_id=r.id WHERE i.id=NEW.payroll_run_item_id FOR UPDATE OF r;
 IF parent_status IN ('LOCKED','PAYMENT_SCHEDULED','PAID') THEN RAISE EXCEPTION 'Cannot attach sources to locked payroll'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_payroll_source_insert BEFORE INSERT ON payroll_run_item_settlements FOR EACH ROW EXECUTE FUNCTION require_open_payroll_source_claim();
CREATE TRIGGER guard_payroll_supplement_insert BEFORE INSERT ON payroll_supplements FOR EACH ROW EXECUTE FUNCTION require_open_payroll_source_claim();
CREATE FUNCTION protect_payroll_calculation_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.entity_type IN ('PAYROLL_RUN','PAYROLL_ITEM') THEN RAISE EXCEPTION 'Payroll calculation snapshots are append-only'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER guard_payroll_calculation_snapshot BEFORE UPDATE OR DELETE ON calculation_snapshots FOR EACH ROW EXECUTE FUNCTION protect_payroll_calculation_snapshot();
