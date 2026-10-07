-- Explicit, audited facts only. No current-status or TIMESTAMPTZ/createdAt history backfill.
CREATE TABLE fleet_policy_versions (
    id UUID PRIMARY KEY,
    policy_code VARCHAR(100) NOT NULL CHECK(length(btrim(policy_code))>0),
    policy_version INTEGER NOT NULL CHECK(policy_version>0),
    mappings JSONB NOT NULL CHECK(jsonb_typeof(mappings)='object'),
    sources JSONB NOT NULL CHECK(jsonb_typeof(sources)='array' AND jsonb_array_length(sources)>0),
    approval_reference TEXT NOT NULL CHECK(length(btrim(approval_reference))>0),
    published_by UUID NOT NULL REFERENCES employees(id),
    published_at TIMESTAMPTZ NOT NULL,
    UNIQUE(policy_code,policy_version)
);
CREATE FUNCTION guard_fleet_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Fleet evidence is append-only; use an audited superseding correction' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER fleet_policy_immutable BEFORE UPDATE OR DELETE ON fleet_policy_versions FOR EACH ROW EXECUTE FUNCTION guard_fleet_immutable();
CREATE FUNCTION validate_fleet_policy() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS(SELECT 1 FROM unnest(ARRAY['membershipStates','capacityStates','activityStates']) field
              WHERE jsonb_typeof(NEW.mappings->field) IS DISTINCT FROM 'object' OR NEW.mappings->field='{}'::jsonb)
       OR EXISTS(SELECT 1 FROM jsonb_each(NEW.mappings->'membershipStates') WHERE length(btrim(key))=0 OR value NOT IN ('true'::jsonb,'false'::jsonb))
       OR EXISTS(SELECT 1 FROM jsonb_each(NEW.mappings->'capacityStates') WHERE length(btrim(key))=0 OR value NOT IN ('true'::jsonb,'false'::jsonb))
       OR EXISTS(SELECT 1 FROM jsonb_each_text(NEW.mappings->'activityStates') WHERE length(btrim(key))=0 OR value NOT IN ('PRODUCTIVE','NON_PRODUCTIVE','EXCLUDED','UNAVAILABLE'))
       OR EXISTS(SELECT 1 FROM jsonb_array_elements(NEW.sources) s WHERE length(btrim(coalesce(s->>'type','')))=0
                  OR length(btrim(coalesce(s->>'reference','')))=0 OR length(btrim(coalesce(s->>'version','')))=0) THEN
        RAISE EXCEPTION 'Explicit nonempty state mappings and qualified source identities required' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER fleet_policy_validate BEFORE INSERT ON fleet_policy_versions FOR EACH ROW EXECUTE FUNCTION validate_fleet_policy();

CREATE TABLE vehicle_status_events (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES fleet_policy_versions(id),
    truck_id UUID NOT NULL REFERENCES trucks(id),
    kind VARCHAR(20) NOT NULL CHECK(kind IN ('MEMBERSHIP','CAPACITY','ACTIVITY')),
    status TEXT NOT NULL CHECK(length(btrim(status))>0),
    classification VARCHAR(20) NOT NULL CHECK(classification IN ('IN','OUT','AVAILABLE','PRODUCTIVE','NON_PRODUCTIVE','EXCLUDED','UNAVAILABLE')),
    occurred_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL CHECK(valid_until>occurred_at),
    source_type TEXT NOT NULL CHECK(length(btrim(source_type))>0),
    source_reference TEXT NOT NULL CHECK(length(btrim(source_reference))>0),
    source_version TEXT NOT NULL CHECK(length(btrim(source_version))>0),
    source_event_id TEXT NOT NULL CHECK(length(btrim(source_event_id))>0),
    trip_id UUID REFERENCES trips(id),
    load_id UUID REFERENCES loads(id),
    execution_reference TEXT,
    supersedes_event_id UUID UNIQUE REFERENCES vehicle_status_events(id),
    reason_code TEXT NOT NULL CHECK(length(btrim(reason_code))>0),
    reason TEXT NOT NULL CHECK(length(btrim(reason))>0),
    captured_by UUID NOT NULL REFERENCES employees(id),
    captured_at TIMESTAMPTZ NOT NULL,
    normalized_input_hash VARCHAR(64) NOT NULL CHECK(normalized_input_hash~'^[0-9a-f]{64}$'),
    UNIQUE(source_type,source_reference,source_version,source_event_id),
    CHECK(classification<>'PRODUCTIVE' OR ((trip_id IS NOT NULL OR load_id IS NOT NULL) AND length(btrim(coalesce(execution_reference,'')))>0))
);
CREATE INDEX ix_vehicle_history_interval ON vehicle_status_events(policy_id,truck_id,occurred_at,valid_until);
CREATE TRIGGER vehicle_history_immutable BEFORE UPDATE OR DELETE ON vehicle_status_events FOR EACH ROW EXECUTE FUNCTION guard_fleet_immutable();
CREATE FUNCTION validate_vehicle_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE policy fleet_policy_versions%ROWTYPE; expected TEXT; prior vehicle_status_events%ROWTYPE;
BEGIN
    SELECT * INTO policy FROM fleet_policy_versions WHERE id=NEW.policy_id;
    IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(policy.sources) s WHERE s->>'type'=NEW.source_type AND s->>'reference'=NEW.source_reference AND s->>'version'=NEW.source_version) THEN
        RAISE EXCEPTION 'Fleet source not registered in exact policy version' USING ERRCODE='23514';
    END IF;
    expected:=CASE NEW.kind
        WHEN 'MEMBERSHIP' THEN CASE policy.mappings #>> ARRAY['membershipStates',NEW.status] WHEN 'true' THEN 'IN' WHEN 'false' THEN 'OUT' ELSE 'UNAVAILABLE' END
        WHEN 'CAPACITY' THEN CASE policy.mappings #>> ARRAY['capacityStates',NEW.status] WHEN 'true' THEN 'AVAILABLE' WHEN 'false' THEN 'EXCLUDED' ELSE 'UNAVAILABLE' END
        ELSE coalesce(policy.mappings #>> ARRAY['activityStates',NEW.status],'UNAVAILABLE') END;
    IF NEW.classification IS DISTINCT FROM expected THEN RAISE EXCEPTION 'Fleet classification must match immutable authored mapping' USING ERRCODE='23514'; END IF;
    IF NEW.supersedes_event_id IS NOT NULL THEN
        SELECT * INTO prior FROM vehicle_status_events WHERE id=NEW.supersedes_event_id FOR UPDATE;
        IF prior.policy_id IS DISTINCT FROM NEW.policy_id OR prior.truck_id IS DISTINCT FROM NEW.truck_id OR prior.kind IS DISTINCT FROM NEW.kind
           OR EXISTS(SELECT 1 FROM vehicle_status_events WHERE supersedes_event_id=prior.id) THEN
            RAISE EXCEPTION 'Fleet correction must append to active matching evidence, without branching' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER vehicle_history_validate BEFORE INSERT ON vehicle_status_events FOR EACH ROW EXECUTE FUNCTION validate_vehicle_history();

CREATE TABLE fleet_mileage_attributions (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES fleet_policy_versions(id),
    truck_id UUID NOT NULL REFERENCES trucks(id),
    trip_id UUID NOT NULL REFERENCES trips(id),
    completed_at TIMESTAMPTZ NOT NULL,
    loaded_miles NUMERIC(12,3) NOT NULL CHECK(loaded_miles>=0),
    empty_miles NUMERIC(12,3) NOT NULL CHECK(empty_miles>=0),
    actual_miles NUMERIC(12,3) NOT NULL CHECK(actual_miles>=loaded_miles+empty_miles),
    source_type TEXT NOT NULL CHECK(length(btrim(source_type))>0),
    source_reference TEXT NOT NULL CHECK(length(btrim(source_reference))>0),
    source_version TEXT NOT NULL CHECK(length(btrim(source_version))>0),
    source_event_id TEXT NOT NULL CHECK(length(btrim(source_event_id))>0),
    supersedes_id UUID UNIQUE REFERENCES fleet_mileage_attributions(id),
    reason_code TEXT NOT NULL CHECK(length(btrim(reason_code))>0),
    reason TEXT NOT NULL CHECK(length(btrim(reason))>0),
    captured_by UUID NOT NULL REFERENCES employees(id),
    captured_at TIMESTAMPTZ NOT NULL,
    normalized_input_hash VARCHAR(64) NOT NULL CHECK(normalized_input_hash~'^[0-9a-f]{64}$'),
    UNIQUE(source_type,source_reference,source_version,source_event_id)
);
CREATE UNIQUE INDEX uq_fleet_trip_original_attribution ON fleet_mileage_attributions(trip_id) WHERE supersedes_id IS NULL;
CREATE INDEX ix_fleet_mileage_period ON fleet_mileage_attributions(policy_id,truck_id,completed_at);
CREATE TRIGGER fleet_mileage_immutable BEFORE UPDATE OR DELETE ON fleet_mileage_attributions FOR EACH ROW EXECUTE FUNCTION guard_fleet_immutable();
CREATE FUNCTION validate_fleet_mileage() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE policy fleet_policy_versions%ROWTYPE; prior fleet_mileage_attributions%ROWTYPE;
BEGIN
    SELECT * INTO policy FROM fleet_policy_versions WHERE id=NEW.policy_id;
    PERFORM id FROM trips WHERE id=NEW.trip_id FOR NO KEY UPDATE;
    IF NOT EXISTS(SELECT 1 FROM jsonb_array_elements(policy.sources) s WHERE s->>'type'=NEW.source_type AND s->>'reference'=NEW.source_reference AND s->>'version'=NEW.source_version)
       OR NOT EXISTS(SELECT 1 FROM trips WHERE id=NEW.trip_id AND completed_at=NEW.completed_at AND cancelled_at IS NULL
                     AND loaded_miles=NEW.loaded_miles AND empty_miles=NEW.empty_miles AND actual_distance_miles=NEW.actual_miles)
       OR NEW.completed_at>NEW.captured_at OR NEW.captured_at>clock_timestamp() THEN
        RAISE EXCEPTION 'Completion attribution requires exact actual miles/completion and qualified audited source; never current truck inference' USING ERRCODE='23514';
    END IF;
    IF NEW.supersedes_id IS NOT NULL THEN
        SELECT * INTO prior FROM fleet_mileage_attributions WHERE id=NEW.supersedes_id FOR UPDATE;
        IF prior.trip_id IS DISTINCT FROM NEW.trip_id OR prior.policy_id IS DISTINCT FROM NEW.policy_id
           OR EXISTS(SELECT 1 FROM fleet_mileage_attributions WHERE supersedes_id=prior.id) THEN
            RAISE EXCEPTION 'Attribution correction must append to active same Trip/policy without branching' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER fleet_mileage_validate BEFORE INSERT ON fleet_mileage_attributions FOR EACH ROW EXECUTE FUNCTION validate_fleet_mileage();
