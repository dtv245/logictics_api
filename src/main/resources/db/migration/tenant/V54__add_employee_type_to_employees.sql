ALTER TABLE public.employees
    ADD COLUMN employee_type VARCHAR(20);

UPDATE public.employees employee
SET employee_type = CASE
    WHEN EXISTS (
        SELECT 1
        FROM public.tenant_roles role
        WHERE role.id = employee.role_id
          AND UPPER(BTRIM(role.normalized_name)) = 'DRIVER'
    ) THEN 'DRIVER'
    ELSE 'OFFICE_STAFF'
END;

ALTER TABLE public.employees
    ALTER COLUMN employee_type SET NOT NULL;

ALTER TABLE public.employees
    ADD CONSTRAINT ck_employees_employee_type
        CHECK (employee_type IN ('OFFICE_STAFF', 'DRIVER'));

CREATE INDEX ix_employees_employee_type
    ON public.employees (employee_type);
