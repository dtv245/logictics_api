CREATE TABLE public.driver_violations (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    violation_date TIMESTAMPTZ NOT NULL,
    violation_type VARCHAR(100) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    fine_amount NUMERIC(18, 2),
    fine_currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    trip_id UUID,
    document_id UUID,
    resolution_status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_violations PRIMARY KEY (id),
    CONSTRAINT fk_driver_violations_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_driver_violations_trip FOREIGN KEY (trip_id)
        REFERENCES public.trips(id) ON DELETE SET NULL,
    CONSTRAINT fk_driver_violations_document FOREIGN KEY (document_id)
        REFERENCES public.documents(id) ON DELETE SET NULL,
    CONSTRAINT ck_driver_violations_fine_amount CHECK (fine_amount IS NULL OR fine_amount >= 0),
    CONSTRAINT ck_driver_violations_fine_currency CHECK (fine_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_driver_violations_resolution_status CHECK (
        resolution_status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'DISPUTED', 'CANCELLED')
    )
);

CREATE INDEX ix_driver_violations_employee_date
    ON public.driver_violations (employee_id, violation_date DESC, id);
CREATE INDEX ix_driver_violations_trip_id ON public.driver_violations (trip_id);
CREATE INDEX ix_driver_violations_document_id ON public.driver_violations (document_id);
