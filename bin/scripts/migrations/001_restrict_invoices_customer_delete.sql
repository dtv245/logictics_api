BEGIN;

ALTER TABLE public.invoices
    DROP CONSTRAINT fk_invoices_customers_customer_id;

ALTER TABLE public.invoices
    ADD CONSTRAINT fk_invoices_customers_customer_id
        FOREIGN KEY (customer_id)
        REFERENCES public.customers(id)
        ON DELETE RESTRICT;

COMMIT;
