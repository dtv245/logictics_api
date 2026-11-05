CREATE TABLE public.employment_contracts (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    contract_number VARCHAR(100) NOT NULL,
    contract_type VARCHAR(32) NOT NULL,
    signed_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    document_id UUID,
    termination_date DATE,
    termination_reason VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_employment_contracts PRIMARY KEY (id),
    CONSTRAINT fk_employment_contracts_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employment_contracts_document FOREIGN KEY (document_id)
        REFERENCES public.documents(id) ON DELETE SET NULL,
    CONSTRAINT ck_employment_contracts_type CHECK (contract_type IN ('PROBATION', 'FIXED_TERM', 'INDEFINITE')),
    CONSTRAINT ck_employment_contracts_dates CHECK (
        signed_date <= effective_date
        AND (termination_date IS NULL OR termination_date >= effective_date)
        AND (
            (contract_type = 'INDEFINITE' AND expiry_date IS NULL)
            OR (contract_type IN ('PROBATION', 'FIXED_TERM') AND expiry_date IS NOT NULL AND expiry_date >= effective_date)
        )
    )
);
CREATE UNIQUE INDEX ux_employment_contracts_contract_number
    ON public.employment_contracts (contract_number);
CREATE INDEX ix_employment_contracts_employee_effective
    ON public.employment_contracts (employee_id, effective_date DESC, created_at DESC);

CREATE TABLE public.driver_insurances (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    social_insurance_number VARCHAR(512),
    social_insurance_number_hmac VARCHAR(64),
    health_insurance_card_number VARCHAR(512),
    health_insurance_card_number_hmac VARCHAR(64),
    registered_clinic VARCHAR(255),
    participation_start_date DATE NOT NULL,
    participation_end_date DATE,
    social_insurance_status VARCHAR(32),
    health_insurance_status VARCHAR(32),
    unemployment_insurance_status VARCHAR(32),
    insured_salary NUMERIC(18,2),
    health_insurance_expiry_date DATE,
    note VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_insurances PRIMARY KEY (id),
    CONSTRAINT fk_driver_insurances_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT,
    CONSTRAINT ck_driver_insurances_participation_dates CHECK (
        participation_end_date IS NULL OR participation_end_date >= participation_start_date
    ),
    CONSTRAINT ck_driver_insurances_insured_salary CHECK (insured_salary IS NULL OR insured_salary >= 0)
);
CREATE INDEX ix_driver_insurances_employee_period
    ON public.driver_insurances (employee_id, participation_start_date DESC, created_at DESC);
CREATE INDEX ix_driver_insurances_social_number_hmac
    ON public.driver_insurances (social_insurance_number_hmac);
CREATE INDEX ix_driver_insurances_health_card_hmac
    ON public.driver_insurances (health_insurance_card_number_hmac);
