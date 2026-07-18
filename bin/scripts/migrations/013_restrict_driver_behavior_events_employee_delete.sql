BEGIN;

ALTER TABLE public.driver_behavior_events
    DROP CONSTRAINT fk_driver_behavior_events_employee_employee_id;

ALTER TABLE public.driver_behavior_events
    ADD CONSTRAINT fk_driver_behavior_events_employee_employee_id
        FOREIGN KEY (employee_id)
        REFERENCES public.employees(id)
        ON DELETE RESTRICT;

COMMIT;
