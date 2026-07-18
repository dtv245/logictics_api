BEGIN;

ALTER TABLE public.documents
    DROP CONSTRAINT fk_documents_employees_employee_id;

ALTER TABLE public.documents
    ADD CONSTRAINT fk_documents_employees_employee_id
        FOREIGN KEY (employee_id)
        REFERENCES public.employees(id)
        ON DELETE RESTRICT;

COMMIT;
