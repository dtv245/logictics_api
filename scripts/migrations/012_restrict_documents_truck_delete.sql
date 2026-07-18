BEGIN;

ALTER TABLE public.documents
    DROP CONSTRAINT fk_documents_trucks_truck_id;

ALTER TABLE public.documents
    ADD CONSTRAINT fk_documents_trucks_truck_id
        FOREIGN KEY (truck_id)
        REFERENCES public.trucks(id)
        ON DELETE RESTRICT;

COMMIT;
