-- Payroll ledger and immutable payslip snapshots. Tax/insurance values are supplied
-- by an explicitly configured jurisdiction policy; this schema embeds no legal rates.

-- Preserve the original settlement cost entry and record reversals as signed credit rows.
ALTER TABLE shipment_costs DROP CONSTRAINT chk_shipment_costs_amount;
ALTER TABLE shipment_costs ADD CONSTRAINT chk_shipment_costs_amount
    CHECK (amount >= 0 OR (
        category = 'DRIVER' AND cost_basis = 'ACTUAL'
        AND source_type IN ('DRIVER_SETTLEMENT_REVERSAL', 'DRIVER_SETTLEMENT_ADJUSTMENT')
    ));

CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY,
    run_number VARCHAR(60) NOT NULL UNIQUE,
    pay_period_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    calculated_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    locked_at TIMESTAMPTZ,
    locked_by UUID,
    paid_at TIMESTAMPTZ,
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_runs_period FOREIGN KEY (pay_period_id) REFERENCES pay_periods(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payroll_runs_status CHECK (status IN ('DRAFT','CALCULATED','IN_REVIEW','LOCKED','PAID','CANCELLED'))
);
CREATE INDEX ix_payroll_runs_period_status ON payroll_runs(pay_period_id, status);

CREATE TABLE payroll_run_items (
    id UUID PRIMARY KEY,
    payroll_run_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    income_tax_amount NUMERIC(19,4),
    insurance_amount NUMERIC(19,4),
    other_deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_amount NUMERIC(19,4),
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_items_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payroll_items_driver FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT uq_payroll_items_run_driver_currency UNIQUE (payroll_run_id, driver_id, currency),
    CONSTRAINT chk_payroll_items_amounts CHECK (gross_amount >= 0 AND other_deduction_amount >= 0 AND reimbursement_amount >= 0
        AND (income_tax_amount IS NULL OR income_tax_amount >= 0)
        AND (insurance_amount IS NULL OR insurance_amount >= 0)
        AND (net_amount IS NULL OR net_amount >= 0)),
    CONSTRAINT chk_payroll_items_status CHECK (status IN ('SCHEDULED','PAYMENT_PENDING','PAID','PAYMENT_FAILED'))
);
CREATE INDEX ix_payroll_items_driver ON payroll_run_items(driver_id, payroll_run_id);

CREATE TABLE payroll_run_item_settlements (
    payroll_run_item_id UUID NOT NULL,
    settlement_id UUID NOT NULL,
    PRIMARY KEY (payroll_run_item_id, settlement_id),
    CONSTRAINT fk_payroll_item_settlements_item FOREIGN KEY (payroll_run_item_id) REFERENCES payroll_run_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payroll_item_settlements_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id) ON DELETE RESTRICT,
    CONSTRAINT uq_payroll_item_settlement UNIQUE (settlement_id)
);

CREATE TABLE payslips (
    id UUID PRIMARY KEY,
    payroll_run_item_id UUID NOT NULL UNIQUE,
    driver_id UUID NOT NULL,
    snapshot_json JSONB NOT NULL,
    pdf_document_id UUID,
    pdf_uri VARCHAR(1000),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payslips_item FOREIGN KEY (payroll_run_item_id) REFERENCES payroll_run_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payslips_driver FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payslips_document FOREIGN KEY (pdf_document_id) REFERENCES documents(id) ON DELETE SET NULL
);
CREATE INDEX ix_payslips_driver_issued ON payslips(driver_id, issued_at DESC);

CREATE TABLE payroll_payments (
    id UUID PRIMARY KEY,
    payroll_run_item_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(200),
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    scheduled_at TIMESTAMPTZ,
    succeeded_at TIMESTAMPTZ,
    reconciled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_payments_item FOREIGN KEY (payroll_run_item_id) REFERENCES payroll_run_items(id) ON DELETE RESTRICT,
    CONSTRAINT uq_payroll_payment_attempt UNIQUE (payroll_run_item_id, attempt_number),
    CONSTRAINT chk_payroll_payment_amount CHECK (amount >= 0),
    CONSTRAINT chk_payroll_payment_attempt CHECK (attempt_number > 0),
    CONSTRAINT chk_payroll_payment_method CHECK (payment_method IN ('BANK_TRANSFER','STRIPE','MANUAL')),
    CONSTRAINT chk_payroll_payment_status CHECK (status IN ('SCHEDULED','SUBMITTED','SUCCEEDED','FAILED','CANCELLED'))
);
CREATE INDEX ix_payroll_payments_item_status ON payroll_payments(payroll_run_item_id, status);
