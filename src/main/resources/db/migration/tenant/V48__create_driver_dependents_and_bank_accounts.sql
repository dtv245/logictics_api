CREATE TABLE public.driver_dependents (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    relationship VARCHAR(64) NOT NULL,
    date_of_birth DATE,
    national_id_number VARCHAR(512),
    national_id_number_hmac VARCHAR(64),
    tax_code VARCHAR(512),
    tax_code_hmac VARCHAR(64),
    deduction_start_date DATE,
    deduction_end_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_dependents PRIMARY KEY (id),
    CONSTRAINT fk_driver_dependents_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT,
    CONSTRAINT ck_driver_dependents_deduction_dates CHECK (
        deduction_end_date IS NULL
        OR (deduction_start_date IS NOT NULL AND deduction_end_date >= deduction_start_date)
    )
);
CREATE INDEX ix_driver_dependents_employee_active_created
    ON public.driver_dependents (employee_id, active, created_at DESC);
CREATE INDEX ix_driver_dependents_national_id_hmac
    ON public.driver_dependents (national_id_number_hmac);
CREATE INDEX ix_driver_dependents_tax_code_hmac
    ON public.driver_dependents (tax_code_hmac);

CREATE TABLE public.driver_bank_accounts (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    account_number VARCHAR(512) NOT NULL,
    account_number_hmac VARCHAR(64) NOT NULL,
    account_holder_name VARCHAR(255) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_bank_accounts PRIMARY KEY (id),
    CONSTRAINT fk_driver_bank_accounts_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT
);
CREATE INDEX ix_driver_bank_accounts_employee_active_created
    ON public.driver_bank_accounts (employee_id, active, created_at DESC);
CREATE INDEX ix_driver_bank_accounts_account_number_hmac
    ON public.driver_bank_accounts (account_number_hmac);
CREATE UNIQUE INDEX ux_driver_bank_accounts_one_primary
    ON public.driver_bank_accounts (employee_id)
    WHERE is_primary = TRUE AND active = TRUE;
