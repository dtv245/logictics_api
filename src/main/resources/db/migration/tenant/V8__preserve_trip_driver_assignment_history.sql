-- Forward fix for V2: deleting a trip must not erase its driver-assignment history.
ALTER TABLE trip_driver_assignments
    DROP CONSTRAINT fk_trip_driver_assignments_trip;

ALTER TABLE trip_driver_assignments
    ADD CONSTRAINT fk_trip_driver_assignments_trip
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE RESTRICT;
