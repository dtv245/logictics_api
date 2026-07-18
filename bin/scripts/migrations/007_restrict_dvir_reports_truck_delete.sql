BEGIN;

ALTER TABLE public.dvir_reports
    DROP CONSTRAINT fk_dvir_reports_truck_truck_id;

ALTER TABLE public.dvir_reports
    ADD CONSTRAINT fk_dvir_reports_truck_truck_id
        FOREIGN KEY (truck_id)
        REFERENCES public.trucks(id)
        ON DELETE RESTRICT;

COMMIT;
