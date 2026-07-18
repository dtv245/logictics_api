BEGIN;

ALTER TABLE public.load_condition_reports
    DROP CONSTRAINT fk_load_condition_reports_load_load_id;

ALTER TABLE public.load_condition_reports
    ADD CONSTRAINT fk_load_condition_reports_load_load_id
        FOREIGN KEY (load_id)
        REFERENCES public.loads(id)
        ON DELETE RESTRICT;

COMMIT;
