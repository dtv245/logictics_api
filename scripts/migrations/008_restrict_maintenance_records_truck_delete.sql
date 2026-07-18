BEGIN;

ALTER TABLE public.maintenance_records
    DROP CONSTRAINT fk_maintenance_records_truck_truck_id;

ALTER TABLE public.maintenance_records
    ADD CONSTRAINT fk_maintenance_records_truck_truck_id
        FOREIGN KEY (truck_id)
        REFERENCES public.trucks(id)
        ON DELETE RESTRICT;

COMMIT;
