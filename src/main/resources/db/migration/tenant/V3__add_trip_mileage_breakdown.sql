-- V3__add_trip_mileage_breakdown.sql
ALTER TABLE trips
    ADD COLUMN planned_distance_miles NUMERIC(12,3),
    ADD COLUMN actual_distance_miles NUMERIC(12,3),
    ADD COLUMN loaded_miles NUMERIC(12,3),
    ADD COLUMN empty_miles NUMERIC(12,3);

COMMENT ON COLUMN trips.total_distance IS 'DEPRECATED: Legacy distance value. Use actual_distance_miles or loaded_miles instead.';
