ALTER TABLE public.driver_licenses
    ALTER COLUMN license_number TYPE VARCHAR(256),
    ADD COLUMN license_number_hmac VARCHAR(64),
    ADD COLUMN issued_place VARCHAR(255),
    ADD COLUMN remaining_points SMALLINT,
    ADD COLUMN front_image_document_id UUID,
    ADD COLUMN back_image_document_id UUID;

ALTER TABLE public.driver_licenses
    ADD CONSTRAINT ck_driver_licenses_remaining_points
        CHECK (remaining_points IS NULL OR remaining_points BETWEEN 0 AND 12);

ALTER TABLE public.driver_licenses
    DROP CONSTRAINT fk_driver_licenses_employee_employee_id,
    ADD CONSTRAINT fk_driver_licenses_employee_employee_id
        FOREIGN KEY (employee_id) REFERENCES public.employees(id) ON DELETE RESTRICT;

ALTER TABLE public.driver_licenses
    ADD CONSTRAINT fk_driver_licenses_front_document
        FOREIGN KEY (front_image_document_id) REFERENCES public.documents(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_driver_licenses_back_document
        FOREIGN KEY (back_image_document_id) REFERENCES public.documents(id) ON DELETE SET NULL;

DROP INDEX public.ix_driver_licenses_employee_id_license_number_issuing_country;
CREATE UNIQUE INDEX ix_driver_licenses_employee_id_license_hmac_country
    ON public.driver_licenses (employee_id, license_number_hmac, issuing_country);
CREATE INDEX ix_driver_licenses_front_image_document_id
    ON public.driver_licenses (front_image_document_id);
CREATE INDEX ix_driver_licenses_back_image_document_id
    ON public.driver_licenses (back_image_document_id);

ALTER TABLE public.documents
    ADD COLUMN expiry_date DATE;

CREATE TABLE public.driver_health_checks (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    checked_date DATE NOT NULL,
    expiry_date DATE NOT NULL,
    facility VARCHAR(255) NOT NULL,
    conclusion VARCHAR(256) NOT NULL,
    document_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_health_checks PRIMARY KEY (id),
    CONSTRAINT ck_driver_health_checks_dates CHECK (expiry_date > checked_date),
    CONSTRAINT fk_driver_health_checks_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_driver_health_checks_document FOREIGN KEY (document_id)
        REFERENCES public.documents(id) ON DELETE SET NULL
);
CREATE INDEX ix_driver_health_checks_employee_expiry
    ON public.driver_health_checks (employee_id, expiry_date);

CREATE TABLE public.driver_certifications (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    issued_date DATE NOT NULL,
    expiry_date DATE,
    issuer VARCHAR(255),
    document_id UUID,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_certifications PRIMARY KEY (id),
    CONSTRAINT ck_driver_certifications_expiry CHECK (expiry_date IS NULL OR expiry_date > issued_date),
    CONSTRAINT fk_driver_certifications_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_driver_certifications_document FOREIGN KEY (document_id)
        REFERENCES public.documents(id) ON DELETE SET NULL
);
CREATE INDEX ix_driver_certifications_employee_expiry
    ON public.driver_certifications (employee_id, expiry_date);
