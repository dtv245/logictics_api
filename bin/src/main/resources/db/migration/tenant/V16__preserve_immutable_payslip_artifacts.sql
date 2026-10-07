-- V11 existing external-document references remain valid; newly issued artifacts persist atomically.
ALTER TABLE payslips ADD COLUMN pdf_content BYTEA;
ALTER TABLE payslips ADD COLUMN issued_by UUID REFERENCES employees(id) ON DELETE RESTRICT;
ALTER TABLE payslips ADD COLUMN renderer_version VARCHAR(60);
ALTER TABLE payslips ADD COLUMN pdf_sha256 VARCHAR(64);
CREATE TRIGGER guard_immutable_payslip BEFORE UPDATE OR DELETE ON payslips FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_version();
CREATE FUNCTION require_locked_payslip_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.issued_by IS NULL OR NEW.pdf_content IS NULL OR NEW.pdf_sha256 IS NULL OR NEW.renderer_version IS NULL
  OR NOT EXISTS(SELECT 1 FROM payroll_run_items i JOIN payroll_runs r ON r.id=i.payroll_run_id
   WHERE i.id=NEW.payroll_run_item_id AND i.driver_id=NEW.driver_id AND i.tax_availability='AVAILABLE'
    AND r.status IN ('LOCKED','PAYMENT_SCHEDULED','PAID'))
  THEN RAISE EXCEPTION 'Payslip requires resolved locked payroll, actor and persisted artifact'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_payslip_source BEFORE INSERT ON payslips FOR EACH ROW EXECUTE FUNCTION require_locked_payslip_source();
