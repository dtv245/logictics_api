BEGIN;

ALTER TABLE public.load_exceptions
    DROP CONSTRAINT fk_load_exceptions_loads_load_id;

ALTER TABLE public.load_exceptions
    ADD CONSTRAINT fk_load_exceptions_loads_load_id
        FOREIGN KEY (load_id)
        REFERENCES public.loads(id)
        ON DELETE RESTRICT;

COMMIT;
