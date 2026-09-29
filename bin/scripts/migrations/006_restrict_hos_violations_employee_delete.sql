BEGIN;

ALTER TABLE public.hos_violations
    DROP CONSTRAINT fk_hos_violations_employees_employee_id;

ALTER TABLE public.hos_violations
    ADD CONSTRAINT fk_hos_violations_employees_employee_id
        FOREIGN KEY (employee_id)
        REFERENCES public.employees(id)
        ON DELETE RESTRICT;

COMMIT;
