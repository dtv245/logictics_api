BEGIN;

ALTER TABLE public.invoices
    DROP CONSTRAINT fk_invoices_employees_employee_id;

ALTER TABLE public.invoices
    ADD CONSTRAINT fk_invoices_employees_employee_id
        FOREIGN KEY (employee_id)
        REFERENCES public.employees(id)
        ON DELETE RESTRICT;

COMMIT;
