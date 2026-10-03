-- V11 is reused. No jurisdiction-specific legal rates or inferred historical values.
CREATE TABLE payroll_jurisdictions (
 id UUID PRIMARY KEY, country_code VARCHAR(2) NOT NULL,
 subdivision_code VARCHAR(80), locality_code VARCHAR(120),
 CONSTRAINT uq_payroll_jurisdiction UNIQUE NULLS NOT DISTINCT(country_code,subdivision_code,locality_code),
 CONSTRAINT chk_payroll_country CHECK(country_code ~ '^[A-Z]{2}$')
);
-- Each tenant has its own database/schema; this singleton belongs to that tenant.
CREATE TABLE tenant_payroll_settings (
 id INTEGER PRIMARY KEY CHECK(id=1), default_jurisdiction_id UUID REFERENCES payroll_jurisdictions(id) ON DELETE RESTRICT
);
INSERT INTO tenant_payroll_settings(id) VALUES(1);
CREATE TABLE employee_payroll_profiles (
 id UUID PRIMARY KEY, employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
 jurisdiction_id UUID REFERENCES payroll_jurisdictions(id) ON DELETE RESTRICT,
 worker_classification VARCHAR(30) NOT NULL CHECK(worker_classification IN ('EMPLOYEE','CONTRACTOR')),
 effective_from DATE NOT NULL, effective_to DATE, profile_version INTEGER NOT NULL CHECK(profile_version>0),
 active BOOLEAN NOT NULL DEFAULT true,
 UNIQUE(employee_id,profile_version), CHECK(effective_to IS NULL OR effective_to>=effective_from)
);
CREATE TABLE payroll_policy_versions (
 id UUID PRIMARY KEY, policy_code VARCHAR(80) NOT NULL,
 jurisdiction_id UUID NOT NULL REFERENCES payroll_jurisdictions(id) ON DELETE RESTRICT,
 worker_classification VARCHAR(30) NOT NULL CHECK(worker_classification IN ('EMPLOYEE','CONTRACTOR')),
 effective_from DATE NOT NULL, effective_to DATE, policy_version INTEGER NOT NULL CHECK(policy_version>0),
 active BOOLEAN NOT NULL DEFAULT true, currency VARCHAR(3) NOT NULL,
 calculator_key VARCHAR(100) NOT NULL, authoritative_source VARCHAR(1000) NOT NULL, configuration_json JSONB NOT NULL,
 UNIQUE(policy_code,policy_version), CHECK(effective_to IS NULL OR effective_to>=effective_from)
);
CREATE FUNCTION protect_payroll_configuration_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Payroll configuration version is immutable; append a new version'; END $$;
CREATE TRIGGER guard_payroll_policy_version BEFORE UPDATE OR DELETE ON payroll_policy_versions FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_version();
CREATE TRIGGER guard_employee_payroll_profile BEFORE UPDATE OR DELETE ON employee_payroll_profiles FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_version();
CREATE TRIGGER guard_payroll_jurisdiction BEFORE UPDATE OR DELETE ON payroll_jurisdictions FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_version();

ALTER TABLE payroll_runs ADD COLUMN request_key VARCHAR(120);
ALTER TABLE payroll_runs ADD COLUMN effective_date DATE;
ALTER TABLE payroll_runs ADD COLUMN calculation_input_json JSONB;
ALTER TABLE payroll_runs ADD COLUMN calculation_snapshot_json JSONB;
ALTER TABLE payroll_runs ADD COLUMN validation_reason VARCHAR(1000);
CREATE UNIQUE INDEX uq_payroll_run_request ON payroll_runs(request_key) WHERE request_key IS NOT NULL;
ALTER TABLE payroll_runs DROP CONSTRAINT chk_payroll_runs_status;
ALTER TABLE payroll_runs ADD CONSTRAINT chk_payroll_runs_status CHECK(status IN ('DRAFT','CALCULATED','VALIDATION_REQUIRED','IN_REVIEW','APPROVED','LOCKED','PAYMENT_SCHEDULED','PAID','CANCELLED'));

ALTER TABLE payroll_run_items ADD COLUMN tax_availability VARCHAR(30) NOT NULL DEFAULT 'UNAVAILABLE';
ALTER TABLE payroll_run_items ADD COLUMN validation_reason VARCHAR(1000);
ALTER TABLE payroll_run_items ADD COLUMN jurisdiction_id UUID REFERENCES payroll_jurisdictions(id) ON DELETE RESTRICT;
ALTER TABLE payroll_run_items ADD COLUMN worker_classification VARCHAR(30);
ALTER TABLE payroll_run_items ADD COLUMN payroll_policy_id UUID REFERENCES payroll_policy_versions(id) ON DELETE RESTRICT;
ALTER TABLE payroll_run_items ADD COLUMN payroll_policy_version INTEGER;
ALTER TABLE payroll_run_items ADD COLUMN effective_date DATE;
ALTER TABLE payroll_run_items ADD COLUMN calculation_snapshot_json JSONB;
ALTER TABLE payroll_run_items ADD CONSTRAINT chk_payroll_tax_availability CHECK(tax_availability IN ('AVAILABLE','UNAVAILABLE'));
-- Reversal components retain economic signs. Unresolved recovery keeps net NULL and requires validation.
ALTER TABLE payroll_run_items DROP CONSTRAINT chk_payroll_items_amounts;
ALTER TABLE payroll_run_items ADD CONSTRAINT chk_payroll_items_amounts CHECK(
 (income_tax_amount IS NULL OR income_tax_amount>=0) AND (insurance_amount IS NULL OR insurance_amount>=0)
 AND (net_amount IS NULL OR net_amount>=0));
ALTER TABLE payroll_run_items DROP CONSTRAINT chk_payroll_items_status;
ALTER TABLE payroll_run_items ADD CONSTRAINT chk_payroll_items_status CHECK(status IN ('CALCULATED','VALIDATION_REQUIRED','SCHEDULED','PAYMENT_PENDING','PAID','PAYMENT_FAILED'));
ALTER TABLE payroll_run_items ADD CONSTRAINT chk_payroll_item_resolved CHECK(
 tax_availability<>'AVAILABLE' OR (payroll_policy_id IS NOT NULL AND payroll_policy_version IS NOT NULL
 AND jurisdiction_id IS NOT NULL AND worker_classification IS NOT NULL AND effective_date IS NOT NULL
 AND income_tax_amount IS NOT NULL AND insurance_amount IS NOT NULL AND net_amount IS NOT NULL AND calculation_snapshot_json IS NOT NULL));

CREATE TABLE payroll_supplements (
 id UUID PRIMARY KEY, payroll_run_item_id UUID NOT NULL REFERENCES payroll_run_items(id) ON DELETE RESTRICT,
 line_class VARCHAR(30) NOT NULL CHECK(line_class IN ('DEDUCTION','REIMBURSEMENT')),
 description VARCHAR(300) NOT NULL, amount NUMERIC(19,4) NOT NULL CHECK(amount>0)
);
CREATE TRIGGER guard_payroll_supplement BEFORE UPDATE OR DELETE ON payroll_supplements FOR EACH ROW EXECUTE FUNCTION protect_payroll_configuration_version();

CREATE FUNCTION require_resolved_payroll_statutory_inputs() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status IN ('APPROVED','LOCKED','PAYMENT_SCHEDULED','PAID') THEN
  IF NOT EXISTS(SELECT 1 FROM payroll_run_items WHERE payroll_run_id=NEW.id)
   OR EXISTS(SELECT 1 FROM payroll_run_items i LEFT JOIN payroll_policy_versions p ON p.id=i.payroll_policy_id
    WHERE i.payroll_run_id=NEW.id AND (i.tax_availability<>'AVAILABLE' OR i.validation_reason IS NOT NULL
     OR i.net_amount IS NULL OR i.jurisdiction_id IS NULL OR i.worker_classification IS NULL OR i.effective_date IS NULL
     OR p.id IS NULL OR p.policy_version<>i.payroll_policy_version OR p.jurisdiction_id<>i.jurisdiction_id
     OR p.worker_classification<>i.worker_classification OR p.currency<>i.currency OR i.currency<>NEW.currency
     OR i.gross_amount<0 OR i.other_deduction_amount<0 OR i.reimbursement_amount<0
     OR i.net_amount<>i.gross_amount-i.other_deduction_amount-i.income_tax_amount-i.insurance_amount+i.reimbursement_amount))
  THEN RAISE EXCEPTION 'Payroll requires resolved statutory policy and reconciled payable amounts'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_payroll_statutory_resolution BEFORE INSERT OR UPDATE ON payroll_runs FOR EACH ROW EXECUTE FUNCTION require_resolved_payroll_statutory_inputs();
