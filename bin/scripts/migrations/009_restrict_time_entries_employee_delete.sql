BEGIN;

ALTER TABLE public.time_entries
    DROP CONSTRAINT fk_time_entries_employees_employee_id;

ALTER TABLE public.time_entries
    ADD CONSTRAINT fk_time_entries_employees_employee_id
        FOREIGN KEY (employee_id)
        REFERENCES public.employees(id)
        ON DELETE RESTRICT;

COMMIT;
