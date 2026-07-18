BEGIN;

ALTER TABLE public.documents
    DROP CONSTRAINT fk_documents_loads_load_id;

ALTER TABLE public.documents
    ADD CONSTRAINT fk_documents_loads_load_id
        FOREIGN KEY (load_id)
        REFERENCES public.loads(id)
        ON DELETE RESTRICT;

COMMIT;
