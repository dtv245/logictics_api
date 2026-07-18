BEGIN;

ALTER TABLE public.invoices
    DROP CONSTRAINT fk_invoices_loads_load_id;

ALTER TABLE public.invoices
    ADD CONSTRAINT fk_invoices_loads_load_id
        FOREIGN KEY (load_id)
        REFERENCES public.loads(id)
        ON DELETE RESTRICT;

COMMIT;
