CREATE TABLE public.driver_operational_statuses (
    id UUID NOT NULL,
    employee_id UUID NOT NULL,
    availability VARCHAR(20) NOT NULL,
    default_vehicle_id UUID,
    last_known_latitude NUMERIC(10, 7),
    last_known_longitude NUMERIC(10, 7),
    last_known_at TIMESTAMPTZ,
    leave_balance_days NUMERIC(6, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT pk_driver_operational_statuses PRIMARY KEY (id),
    CONSTRAINT uq_driver_operational_statuses_employee UNIQUE (employee_id),
    CONSTRAINT fk_driver_operational_statuses_employee FOREIGN KEY (employee_id)
        REFERENCES public.employees(id) ON DELETE CASCADE,
    CONSTRAINT fk_driver_operational_statuses_vehicle FOREIGN KEY (default_vehicle_id)
        REFERENCES public.trucks(id) ON DELETE SET NULL,
    CONSTRAINT ck_driver_operational_statuses_availability CHECK (
        availability IN ('AVAILABLE', 'ON_TRIP', 'OFF_DUTY', 'ON_LEAVE', 'SUSPENDED')
    ),
    CONSTRAINT ck_driver_operational_statuses_location CHECK (
        (last_known_latitude IS NULL AND last_known_longitude IS NULL AND last_known_at IS NULL)
        OR (
            last_known_latitude IS NOT NULL AND last_known_longitude IS NOT NULL AND last_known_at IS NOT NULL
            AND last_known_latitude BETWEEN -90 AND 90
            AND last_known_longitude BETWEEN -180 AND 180
        )
    ),
    CONSTRAINT ck_driver_operational_statuses_leave_balance CHECK (
        leave_balance_days IS NULL OR leave_balance_days >= 0
    )
);
CREATE INDEX ix_driver_operational_statuses_availability_employee
    ON public.driver_operational_statuses (availability, employee_id);
