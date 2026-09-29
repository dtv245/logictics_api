BEGIN;

ALTER TABLE public.hos_logs
    DROP CONSTRAINT fk_hos_logs_employees_employee_id;

ALTER TABLE public.hos_logs
    ADD CONSTRAINT fk_hos_logs_employees_employee_id
        FOREIGN KEY (employee_id)
        REFERENCES public.employees(id)
        ON DELETE RESTRICT;

COMMIT;
