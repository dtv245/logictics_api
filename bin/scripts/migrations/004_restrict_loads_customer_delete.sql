BEGIN;

ALTER TABLE public.loads
    DROP CONSTRAINT fk_loads_customers_customer_id;

ALTER TABLE public.loads
    ADD CONSTRAINT fk_loads_customers_customer_id
        FOREIGN KEY (customer_id)
        REFERENCES public.customers(id)
        ON DELETE RESTRICT;

COMMIT;
