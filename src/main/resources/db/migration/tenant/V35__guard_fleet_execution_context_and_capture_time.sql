-- Forward-only self-review correction; V34 is already applied/verified and is unchanged.
CREATE FUNCTION guard_fleet_execution_evidence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.captured_at>clock_timestamp()
       OR (NEW.kind='ACTIVITY' AND NEW.occurred_at>NEW.captured_at)
       OR (NEW.trip_id IS NOT NULL AND NEW.load_id IS NOT NULL AND NOT EXISTS
            (SELECT 1 FROM trip_stops WHERE trip_id=NEW.trip_id AND load_id=NEW.load_id)) THEN
        RAISE EXCEPTION 'Actual fleet execution evidence must have coherent owned Trip/Load context and capture time' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER fleet_execution_evidence_guard BEFORE INSERT ON vehicle_status_events
    FOR EACH ROW EXECUTE FUNCTION guard_fleet_execution_evidence();
