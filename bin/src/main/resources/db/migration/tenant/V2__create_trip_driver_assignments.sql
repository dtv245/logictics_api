-- V2__create_trip_driver_assignments.sql
CREATE TABLE trip_driver_assignments (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    assignment_type VARCHAR(30) NOT NULL DEFAULT 'PRIMARY',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    planned_miles NUMERIC(12,3),
    actual_miles NUMERIC(12,3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),

    CONSTRAINT fk_trip_driver_assignments_trip 
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_driver_assignments_driver 
        FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE RESTRICT
);

CREATE INDEX ix_trip_driver_assignments_trip ON trip_driver_assignments(trip_id);
CREATE INDEX ix_trip_driver_assignments_driver_period ON trip_driver_assignments(driver_id, effective_from, effective_to);
