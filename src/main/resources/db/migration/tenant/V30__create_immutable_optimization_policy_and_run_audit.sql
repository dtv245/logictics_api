-- Database-per-tenant. No status, provider, eligibility, forecast or policy is seeded.
CREATE TABLE optimization_policy_versions (
    id UUID PRIMARY KEY,
    policy_code VARCHAR(100) NOT NULL CHECK (length(btrim(policy_code)) > 0),
    policy_version INTEGER NOT NULL CHECK (policy_version > 0),
    eligibility_source_policy JSONB NOT NULL CHECK (jsonb_typeof(eligibility_source_policy) = 'object'),
    scoring_policy JSONB NOT NULL CHECK (jsonb_typeof(scoring_policy) = 'object'),
    approval_reference TEXT NOT NULL CHECK (length(btrim(approval_reference)) > 0),
    published_by UUID NOT NULL REFERENCES employees(id),
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE (policy_code, policy_version)
);

CREATE TABLE optimization_runs (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES optimization_policy_versions(id),
    created_at TIMESTAMPTZ NOT NULL,
    planning_until TIMESTAMPTZ NOT NULL CHECK (planning_until = created_at + INTERVAL '72 hours'),
    created_by UUID NOT NULL REFERENCES employees(id),
    idempotency_key VARCHAR(200) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    normalized_input_hash VARCHAR(64) NOT NULL CHECK (normalized_input_hash ~ '^[0-9a-f]{64}$'),
    request_snapshot JSONB NOT NULL CHECK (jsonb_typeof(request_snapshot) = 'object'),
    policy_snapshot JSONB NOT NULL CHECK (jsonb_typeof(policy_snapshot) = 'object'),
    calculated_at TIMESTAMPTZ NOT NULL CHECK (calculated_at >= created_at),
    duration_millis BIGINT NOT NULL CHECK (duration_millis >= 0),
    correlation_id VARCHAR(200) NOT NULL,
    UNIQUE (idempotency_key)
);

CREATE TABLE optimization_assignments (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES optimization_runs(id),
    load_id UUID NOT NULL REFERENCES loads(id),
    trip_id UUID NOT NULL REFERENCES trips(id),
    driver_id UUID NOT NULL REFERENCES employees(id),
    truck_id UUID NOT NULL REFERENCES trucks(id),
    rating_snapshot_id UUID REFERENCES accepted_rating_snapshots(id),
    feasible BOOLEAN NOT NULL,
    rejection_codes JSONB NOT NULL CHECK (jsonb_typeof(rejection_codes) = 'array'),
    final_score NUMERIC NOT NULL DEFAULT NULL,
    rank INTEGER,
    input_fingerprint VARCHAR(64) NOT NULL CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    explanation JSONB NOT NULL CHECK (jsonb_typeof(explanation) = 'object'),
    UNIQUE (run_id, load_id, trip_id, driver_id, truck_id),
    UNIQUE (id, run_id),
    CHECK ((feasible AND rating_snapshot_id IS NOT NULL AND jsonb_array_length(rejection_codes) = 0
            AND final_score IS NOT NULL AND final_score >= 0 AND final_score <= 1
            AND scale(final_score) = 8 AND rank IS NOT NULL AND rank > 0)
        OR (NOT feasible AND jsonb_array_length(rejection_codes) > 0 AND final_score IS NULL AND rank IS NULL))
);
CREATE INDEX ix_optimization_assignments_rank ON optimization_assignments(run_id, rank) WHERE feasible;
CREATE INDEX ix_optimization_assignments_load ON optimization_assignments(load_id);

CREATE FUNCTION guard_optimization_immutable_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Published optimization policy/run/candidate facts are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER optimization_policy_immutable BEFORE UPDATE OR DELETE ON optimization_policy_versions
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();
CREATE TRIGGER optimization_run_immutable BEFORE UPDATE OR DELETE ON optimization_runs
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();
CREATE TRIGGER optimization_assignment_immutable BEFORE UPDATE OR DELETE ON optimization_assignments
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();

CREATE FUNCTION optimization_half_even_8(value NUMERIC) RETURNS NUMERIC LANGUAGE SQL IMMUTABLE STRICT AS $$
    SELECT round((trunc(value * 100000000) + CASE
        WHEN value * 100000000 - trunc(value * 100000000) > 0.5
          OR (value * 100000000 - trunc(value * 100000000) = 0.5
              AND mod(trunc(value * 100000000), 2) = 1) THEN 1 ELSE 0 END) / 100000000, 8)
$$;

CREATE FUNCTION guard_optimization_audit_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE component JSONB;
DECLARE component_name TEXT;
DECLARE contribution NUMERIC;
DECLARE total NUMERIC := 0;
DECLARE weights_total NUMERIC := 0;
DECLARE weight_value NUMERIC;
DECLARE policy JSONB;
DECLARE allowed JSONB;
DECLARE registration JSONB;
BEGIN
    IF TG_TABLE_NAME = 'optimization_policy_versions' THEN
        policy := NEW.scoring_policy;
        IF NEW.eligibility_source_policy ->> 'eligibilityCode' IS DISTINCT FROM 'OPT_ELIGIBILITY_V1'
            OR NEW.eligibility_source_policy ->> 'eligibilityVersion' IS DISTINCT FROM '1'
            OR NEW.eligibility_source_policy ->> 'planningHorizonHours' IS DISTINCT FROM '72'
            OR NEW.eligibility_source_policy ->> 'sourceCode' IS DISTINCT FROM 'OPT_SOURCE_V1'
            OR NEW.eligibility_source_policy ->> 'sourceVersion' IS DISTINCT FROM '1'
            OR policy #>> '{utilityPolicy,code}' IS DISTINCT FROM 'OPT_UTILITY_V1'
            OR policy #>> '{utilityPolicy,version}' IS DISTINCT FROM '1'
            OR policy #>> '{weightPolicy,code}' IS DISTINCT FROM 'OPT_WEIGHT_V1'
            OR policy #>> '{weightPolicy,version}' IS DISTINCT FROM '1'
            OR policy #>> '{numericPolicy,code}' IS DISTINCT FROM 'OPT_NUMERIC_V1'
            OR policy #>> '{numericPolicy,version}' IS DISTINCT FROM '1'
            OR policy #>> '{numeric,intermediatePrecision}' IS DISTINCT FROM '34'
            OR policy #>> '{numeric,roundingMode}' IS DISTINCT FROM 'HALF_EVEN'
            OR policy #>> '{numeric,utilityScale}' IS DISTINCT FROM '6'
            OR policy #>> '{numeric,weightScale}' IS DISTINCT FROM '6'
            OR policy #>> '{numeric,contributionScale}' IS DISTINCT FROM '8'
            OR policy #>> '{numeric,scoreScale}' IS DISTINCT FROM '8' THEN
            RAISE EXCEPTION 'Explicit confirmed V1 policy contracts required' USING ERRCODE = '23514';
        END IF;
        IF policy -> 'curves' IS DISTINCT FROM $curves$
        {
            "DEADHEAD":[{"raw":0,"utility":1},{"raw":0.10,"utility":0.90},{"raw":0.25,"utility":0.70},{"raw":0.50,"utility":0.40},{"raw":1,"utility":0}],
            "MARGIN":[{"raw":0,"utility":0},{"raw":0.05,"utility":0.25},{"raw":0.10,"utility":0.50},{"raw":0.20,"utility":0.80},{"raw":0.30,"utility":1}],
            "ON_TIME":[{"raw":0,"utility":0},{"raw":30,"utility":0.25},{"raw":60,"utility":0.50},{"raw":120,"utility":0.80},{"raw":180,"utility":1}],
            "HOS":[{"raw":0,"utility":0},{"raw":0.05,"utility":0.25},{"raw":0.10,"utility":0.50},{"raw":0.20,"utility":0.80},{"raw":0.30,"utility":1}]
        }$curves$::jsonb THEN
            RAISE EXCEPTION 'Exact confirmed V1 utility curves required' USING ERRCODE = '23514';
        END IF;
        FOREACH component_name IN ARRAY ARRAY['LOAD','TRIP','DRIVER','TRUCK'] LOOP
            allowed := NEW.eligibility_source_policy #> ARRAY['statusAllowlists', component_name];
            IF jsonb_typeof(allowed) IS DISTINCT FROM 'array' OR jsonb_array_length(allowed) = 0 THEN
                RAISE EXCEPTION 'Authored status allowlists required; no implicit ACTIVE/OPEN' USING ERRCODE = '23514';
            END IF;
            IF EXISTS (SELECT 1 FROM jsonb_array_elements(allowed) value WHERE jsonb_typeof(value) <> 'string' OR length(btrim(value #>> '{}')) = 0) THEN
                RAISE EXCEPTION 'Exact nonempty status literals required' USING ERRCODE = '23514';
            END IF;
        END LOOP;
        FOREACH component_name IN ARRAY ARRAY['VEHICLE_LOCATION','DRIVER_AVAILABILITY','TRUCK_AVAILABILITY','ROUTE','CAPACITY','QUALIFICATION','HOS','FORECAST_COST'] LOOP
            allowed := NEW.eligibility_source_policy #> ARRAY['qualifiedSources', component_name];
            IF jsonb_typeof(allowed) IS DISTINCT FROM 'array' OR jsonb_array_length(allowed) = 0 THEN
                RAISE EXCEPTION 'Qualified sources required; no default provider' USING ERRCODE = '23514';
            END IF;
            FOR registration IN SELECT value FROM jsonb_array_elements(allowed) LOOP
                IF length(btrim(coalesce(registration ->> 'type',''))) = 0
                    OR length(btrim(coalesce(registration ->> 'reference',''))) = 0
                    OR length(btrim(coalesce(registration ->> 'version',''))) = 0
                    OR coalesce(registration ->> 'classification','') NOT IN ('AUTHORITATIVE_DB','TRUSTED_ADAPTER')
                    OR (component_name = 'QUALIFICATION' AND registration ->> 'classification' <> 'AUTHORITATIVE_DB') THEN
                    RAISE EXCEPTION 'Versioned qualified source/classification required' USING ERRCODE = '23514';
                END IF;
            END LOOP;
        END LOOP;
        FOREACH component_name IN ARRAY ARRAY['DEADHEAD','MARGIN','ON_TIME','HOS'] LOOP
            weight_value := (policy #>> ARRAY['weights',component_name])::numeric;
            IF weight_value IS NULL OR weight_value IS DISTINCT FROM (CASE component_name
                WHEN 'DEADHEAD' THEN 0.25 WHEN 'MARGIN' THEN 0.35 WHEN 'ON_TIME' THEN 0.30 ELSE 0.10 END) THEN
                RAISE EXCEPTION 'All four approved weights required' USING ERRCODE = '23514';
            END IF;
            weights_total := weights_total + weight_value;
        END LOOP;
        IF weights_total <> 1 THEN RAISE EXCEPTION 'Weights must sum exactly to one' USING ERRCODE = '23514'; END IF;
    ELSIF TG_TABLE_NAME = 'optimization_runs' THEN
        SELECT jsonb_build_object('eligibilitySourcePolicy', eligibility_source_policy, 'scoringPolicy', scoring_policy)
            INTO policy FROM optimization_policy_versions WHERE id = NEW.policy_id;
        IF NEW.policy_snapshot IS DISTINCT FROM policy THEN
            RAISE EXCEPTION 'Run must retain exact published policy' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.explanation ->> 'feasible' IS DISTINCT FROM NEW.feasible::text
            OR NEW.explanation -> 'rejectionCodes' IS DISTINCT FROM NEW.rejection_codes THEN
            RAISE EXCEPTION 'Candidate rejection audit must reconcile' USING ERRCODE = '23514';
        END IF;
        IF NEW.rating_snapshot_id IS NOT NULL AND NOT EXISTS
            (SELECT 1 FROM accepted_rating_snapshots WHERE id = NEW.rating_snapshot_id AND load_id = NEW.load_id) THEN
            RAISE EXCEPTION 'Candidate rating must belong to its Load' USING ERRCODE = '23514';
        END IF;
        IF NEW.feasible THEN
            SELECT policy_snapshot -> 'scoringPolicy' INTO policy FROM optimization_runs WHERE id = NEW.run_id;
            IF NEW.explanation #> '{score,policy}' IS DISTINCT FROM policy
                OR (NEW.explanation #>> '{score,finalScore}')::numeric IS DISTINCT FROM NEW.final_score THEN
                RAISE EXCEPTION 'Score must retain exact policy and result' USING ERRCODE = '23514';
            END IF;
            FOREACH component_name IN ARRAY ARRAY['DEADHEAD','MARGIN','ON_TIME','HOS'] LOOP
                component := NEW.explanation #> ARRAY['score','components',component_name];
                contribution := (component ->> 'contribution')::numeric;
                weight_value := (component ->> 'weight')::numeric;
                IF component ->> 'rawValue' IS NULL OR component ->> 'rawUnit' IS NULL OR contribution IS NULL
                    OR scale(contribution) <> 8 OR weight_value IS NULL
                    OR weight_value IS DISTINCT FROM (policy #>> ARRAY['weights',component_name])::numeric
                    OR scale(weight_value) <> 6 OR component ->> 'normalizedUtility' IS NULL
                    OR scale((component ->> 'normalizedUtility')::numeric) <> 6
                    OR (component ->> 'normalizedUtility')::numeric < 0 OR (component ->> 'normalizedUtility')::numeric > 1 THEN
                    RAISE EXCEPTION 'Complete exact-precision component audit required' USING ERRCODE = '23514';
                END IF;
                total := total + contribution;
                IF contribution IS DISTINCT FROM optimization_half_even_8((component ->> 'normalizedUtility')::numeric * weight_value) THEN
                    RAISE EXCEPTION 'Contribution must follow optimizer HALF_EVEN policy' USING ERRCODE = '23514';
                END IF;
            END LOOP;
            IF NEW.final_score IS DISTINCT FROM total THEN
                RAISE EXCEPTION 'Final score must equal persisted contributions' USING ERRCODE = '23514';
            END IF;
        ELSIF NEW.explanation -> 'score' IS NOT NULL AND NEW.explanation -> 'score' <> 'null'::jsonb THEN
            RAISE EXCEPTION 'Infeasible candidates cannot be scored' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER optimization_policy_insert_guard BEFORE INSERT ON optimization_policy_versions
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_audit_insert();
CREATE TRIGGER optimization_run_insert_guard BEFORE INSERT ON optimization_runs
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_audit_insert();
CREATE TRIGGER optimization_assignment_insert_guard BEFORE INSERT ON optimization_assignments
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_audit_insert();
