-- V4__extend_trip_stops_execution.sql
ALTER TABLE trip_stops
    ADD COLUMN status VARCHAR(40) DEFAULT 'PENDING',
    ADD COLUMN appointment_start TIMESTAMPTZ,
    ADD COLUMN appointment_end TIMESTAMPTZ,
    ADD COLUMN service_started_at TIMESTAMPTZ,
    ADD COLUMN service_completed_at TIMESTAMPTZ,
    ADD COLUMN departed_at TIMESTAMPTZ;

CREATE INDEX ix_trip_stops_trip_status ON trip_stops(trip_id, status);
