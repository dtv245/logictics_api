-- Consequential workflow metadata is separate from the immutable calculated run.
CREATE TABLE optimization_acceptances (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL UNIQUE REFERENCES optimization_runs(id),
    candidate_id UUID NOT NULL UNIQUE REFERENCES optimization_assignments(id),
    load_id UUID NOT NULL UNIQUE REFERENCES loads(id),
    trip_id UUID NOT NULL REFERENCES trips(id),
    driver_id UUID NOT NULL REFERENCES employees(id),
    truck_id UUID NOT NULL REFERENCES trucks(id),
    driver_assignment_id UUID NOT NULL REFERENCES trip_driver_assignments(id),
    original_input_fingerprint VARCHAR(64) NOT NULL CHECK (original_input_fingerprint ~ '^[0-9a-f]{64}$'),
    revalidation_snapshot JSONB NOT NULL CHECK (jsonb_typeof(revalidation_snapshot)='object'),
    accepted_by UUID NOT NULL REFERENCES employees(id),
    accepted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_optimization_acceptance_driver ON optimization_acceptances(driver_id,trip_id);
CREATE INDEX ix_optimization_acceptance_truck ON optimization_acceptances(truck_id,trip_id);
CREATE TRIGGER optimization_acceptance_immutable BEFORE UPDATE OR DELETE ON optimization_acceptances
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();

CREATE TABLE optimization_accept_commands (
    operation VARCHAR(40) NOT NULL CHECK (operation='OPTIMIZATION_ACCEPT'),
    idempotency_key VARCHAR(200) NOT NULL CHECK (length(btrim(idempotency_key))>0),
    normalized_input_hash VARCHAR(64) NOT NULL CHECK (normalized_input_hash ~ '^[0-9a-f]{64}$'),
    acceptance_id UUID NOT NULL REFERENCES optimization_acceptances(id),
    recorded_by UUID NOT NULL REFERENCES employees(id),
    recorded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(operation,idempotency_key)
);
CREATE TRIGGER optimization_accept_command_immutable BEFORE UPDATE OR DELETE ON optimization_accept_commands
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();

CREATE FUNCTION guard_optimization_acceptance_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE candidate optimization_assignments%ROWTYPE;
DECLARE run optimization_runs%ROWTYPE;
DECLARE assignment trip_driver_assignments%ROWTYPE;
BEGIN
    -- Same ordering as the application; NO KEY UPDATE permits FK checks while serializing actual mutations.
    PERFORM id FROM loads WHERE id=NEW.load_id FOR NO KEY UPDATE;
    PERFORM id FROM trips WHERE id=NEW.trip_id FOR NO KEY UPDATE;
    PERFORM id FROM employees WHERE id=NEW.driver_id FOR NO KEY UPDATE;
    PERFORM id FROM trucks WHERE id=NEW.truck_id FOR NO KEY UPDATE;
    SELECT * INTO candidate FROM optimization_assignments WHERE id=NEW.candidate_id;
    SELECT * INTO run FROM optimization_runs WHERE id=NEW.run_id;
    SELECT * INTO assignment FROM trip_driver_assignments WHERE id=NEW.driver_assignment_id;
    IF candidate.run_id IS DISTINCT FROM NEW.run_id OR NOT candidate.feasible
        OR candidate.load_id IS DISTINCT FROM NEW.load_id OR candidate.trip_id IS DISTINCT FROM NEW.trip_id
        OR candidate.driver_id IS DISTINCT FROM NEW.driver_id OR candidate.truck_id IS DISTINCT FROM NEW.truck_id
        OR candidate.input_fingerprint IS DISTINCT FROM NEW.original_input_fingerprint
        OR NEW.accepted_at < run.calculated_at OR NEW.accepted_at >= run.planning_until
        OR NEW.accepted_at > clock_timestamp()
        OR assignment.trip_id IS DISTINCT FROM NEW.trip_id OR assignment.driver_id IS DISTINCT FROM NEW.driver_id
        OR assignment.effective_from >= run.planning_until
        OR (assignment.effective_to IS NOT NULL AND assignment.effective_to <= NEW.accepted_at)
        OR NOT EXISTS (SELECT 1 FROM trips WHERE id=NEW.trip_id AND truck_id=NEW.truck_id
                       AND dispatched_at IS NULL AND completed_at IS NULL AND cancelled_at IS NULL)
        OR NEW.revalidation_snapshot ->> 'feasible' IS DISTINCT FROM 'true'
        OR NEW.revalidation_snapshot -> 'rejectionCodes' IS DISTINCT FROM '[]'::jsonb
        OR NEW.revalidation_snapshot #>> '{context,loadId}' IS DISTINCT FROM NEW.load_id::text
        OR NEW.revalidation_snapshot #>> '{context,tripId}' IS DISTINCT FROM NEW.trip_id::text
        OR NEW.revalidation_snapshot #>> '{context,driverId}' IS DISTINCT FROM NEW.driver_id::text
        OR NEW.revalidation_snapshot #>> '{context,truckId}' IS DISTINCT FROM NEW.truck_id::text
        OR (NEW.revalidation_snapshot #>> '{context,planningStart}')::timestamptz IS DISTINCT FROM run.created_at
        OR (NEW.revalidation_snapshot #>> '{context,planningEnd}')::timestamptz IS DISTINCT FROM run.planning_until
        OR length(btrim(coalesce(run.request_snapshot ->> 'tenantScope',''))) = 0
        OR jsonb_typeof(run.request_snapshot -> 'sourceSelections') IS DISTINCT FROM 'array'
        OR coalesce(NEW.revalidation_snapshot ->> 'databaseStateFingerprint','') !~ '^[0-9a-f]{64}$'
        OR NEW.revalidation_snapshot #> '{score,policy}' IS DISTINCT FROM (run.policy_snapshot -> 'scoringPolicy')
        OR (NEW.revalidation_snapshot #>> '{score,finalScore}')::numeric IS DISTINCT FROM candidate.final_score THEN
        RAISE EXCEPTION 'Acceptance must retain exact feasible candidate, current assigned resources and approved score' USING ERRCODE='23514';
    END IF;
    IF EXISTS (SELECT 1 FROM optimization_acceptances other
        JOIN optimization_runs prior ON prior.id=other.run_id JOIN trip_driver_assignments held ON held.id=other.driver_assignment_id
        WHERE other.trip_id<>NEW.trip_id AND (other.driver_id=NEW.driver_id OR other.truck_id=NEW.truck_id)
          AND prior.created_at<run.planning_until AND prior.planning_until>run.created_at
          AND (held.effective_to IS NULL OR held.effective_to>run.created_at)) THEN
        RAISE EXCEPTION 'An overlapping accepted resource claim already exists' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER optimization_acceptance_insert BEFORE INSERT ON optimization_acceptances
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_acceptance_insert();

-- Legacy authoring paths participate in serialization too; restrictions apply only to optimizer-owned evidence/claims.
CREATE FUNCTION guard_optimization_owned_assignment() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM id FROM employees WHERE id=NEW.driver_id FOR NO KEY UPDATE;
    IF TG_OP='UPDATE' AND EXISTS(SELECT 1 FROM optimization_acceptances WHERE driver_assignment_id=OLD.id)
       AND (NEW.driver_id IS DISTINCT FROM OLD.driver_id OR NEW.trip_id IS DISTINCT FROM OLD.trip_id
            OR NEW.effective_from IS DISTINCT FROM OLD.effective_from OR NEW.assignment_type IS DISTINCT FROM OLD.assignment_type) THEN
        RAISE EXCEPTION 'Accepted assignment identity/history cannot be rewritten' USING ERRCODE='23514';
    END IF;
    IF EXISTS (SELECT 1 FROM optimization_acceptances accepted
        JOIN optimization_runs run ON run.id=accepted.run_id JOIN trip_driver_assignments held ON held.id=accepted.driver_assignment_id
        WHERE accepted.driver_id=NEW.driver_id AND held.id<>NEW.id
          AND NEW.effective_from<run.planning_until AND (NEW.effective_to IS NULL OR NEW.effective_to>run.created_at)
          AND (held.effective_to IS NULL OR held.effective_to>NEW.effective_from)) THEN
        RAISE EXCEPTION 'Assignment overlaps an accepted optimization resource claim' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER optimization_owned_assignment_guard BEFORE INSERT OR UPDATE ON trip_driver_assignments
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_owned_assignment();

CREATE FUNCTION guard_optimization_owned_truck() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.truck_id IS NOT NULL THEN
        PERFORM id FROM trucks WHERE id=NEW.truck_id FOR NO KEY UPDATE;
    END IF;
    IF NEW.truck_id IS DISTINCT FROM OLD.truck_id AND EXISTS (
        SELECT 1 FROM optimization_acceptances accepted JOIN optimization_runs run ON run.id=accepted.run_id
        JOIN trip_driver_assignments held ON held.id=accepted.driver_assignment_id
        WHERE run.planning_until>CURRENT_TIMESTAMP AND (held.effective_to IS NULL OR held.effective_to>CURRENT_TIMESTAMP)
          AND (accepted.trip_id=NEW.id OR (NEW.truck_id=accepted.truck_id AND accepted.trip_id<>NEW.id))) THEN
        RAISE EXCEPTION 'Truck mutation conflicts with an active accepted optimization claim' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER optimization_owned_truck_guard BEFORE UPDATE OF truck_id ON trips
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_owned_truck();
