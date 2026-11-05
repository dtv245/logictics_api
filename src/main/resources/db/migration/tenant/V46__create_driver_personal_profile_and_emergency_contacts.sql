CREATE TABLE public.driver_profiles (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(16),
    nationality VARCHAR(80),
    marital_status VARCHAR(24),
    education_level VARCHAR(80),
    national_id_number VARCHAR(512),
    national_id_number_hmac VARCHAR(64),
    national_id_issued_date DATE,
    national_id_issued_place VARCHAR(255),
    personal_tax_code VARCHAR(512),
    personal_tax_code_hmac VARCHAR(64),
    avatar_url VARCHAR(2048),
    permanent_address VARCHAR(1000),
    temporary_address VARCHAR(1000),
    hometown VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_profiles PRIMARY KEY (id),
    CONSTRAINT fk_driver_profiles_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_driver_profiles_employee_id
    ON public.driver_profiles (employee_id);
CREATE UNIQUE INDEX ux_driver_profiles_national_id_hmac
    ON public.driver_profiles (national_id_number_hmac);
CREATE UNIQUE INDEX ux_driver_profiles_personal_tax_code_hmac
    ON public.driver_profiles (personal_tax_code_hmac);

CREATE TABLE public.driver_emergency_contacts (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    relationship VARCHAR(64) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    address VARCHAR(1000),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_emergency_contacts PRIMARY KEY (id),
    CONSTRAINT fk_driver_emergency_contacts_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE CASCADE
);
CREATE INDEX ix_driver_emergency_contacts_employee_active_created
    ON public.driver_emergency_contacts (employee_id, active, created_at);
CREATE UNIQUE INDEX ux_driver_emergency_contacts_one_primary
    ON public.driver_emergency_contacts (employee_id)
    WHERE is_primary = TRUE AND active = TRUE;
