-- Explicit authoritative records, never a backfill from bare capacity/status.
CREATE TABLE optimization_qualified_inputs (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES optimization_policy_versions(id),
    input_kind VARCHAR(40) NOT NULL CHECK (input_kind IN ('CAPACITY','QUALIFICATION','FORECAST_COST')),
    load_id UUID NOT NULL REFERENCES loads(id),
    trip_id UUID NOT NULL REFERENCES trips(id),
    driver_id UUID NOT NULL REFERENCES employees(id),
    truck_id UUID NOT NULL REFERENCES trucks(id),
    source_type TEXT NOT NULL CHECK (length(btrim(source_type)) > 0),
    source_reference TEXT NOT NULL CHECK (length(btrim(source_reference)) > 0),
    source_version TEXT NOT NULL CHECK (length(btrim(source_version)) > 0),
    source_class VARCHAR(30) NOT NULL CHECK (source_class IN ('AUTHORITATIVE_DB','TRUSTED_ADAPTER')),
    unit VARCHAR(40) NOT NULL CHECK (length(btrim(unit)) > 0),
    observed_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    max_age_seconds BIGINT CHECK (max_age_seconds > 0),
    evidence_version INTEGER NOT NULL CHECK (evidence_version > 0),
    supersedes_input_id UUID UNIQUE REFERENCES optimization_qualified_inputs(id),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    approval_reference TEXT NOT NULL CHECK (length(btrim(approval_reference)) > 0),
    reason_code VARCHAR(80) NOT NULL CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,79}$'),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
    captured_by UUID NOT NULL REFERENCES employees(id),
    captured_at TIMESTAMPTZ NOT NULL CHECK (captured_at >= observed_at),
    normalized_input_hash VARCHAR(64) NOT NULL CHECK (normalized_input_hash ~ '^[0-9a-f]{64}$'),
    CHECK (expires_at IS NOT NULL OR max_age_seconds IS NOT NULL),
    CHECK (expires_at IS NULL OR expires_at > observed_at),
    CHECK (input_kind <> 'QUALIFICATION' OR (source_class = 'AUTHORITATIVE_DB' AND unit = 'QUALIFICATION')),
    CHECK (input_kind <> 'CAPACITY' OR unit = 'POUND'),
    CHECK (input_kind <> 'FORECAST_COST' OR unit ~ '^[A-Z]{3}$')
);
CREATE INDEX ix_optimization_qualified_context ON optimization_qualified_inputs(load_id,trip_id,driver_id,truck_id,input_kind);
CREATE TRIGGER optimization_qualified_input_immutable BEFORE UPDATE OR DELETE ON optimization_qualified_inputs
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();

CREATE TABLE optimization_qualified_forecast_costs (
    qualified_input_id UUID NOT NULL REFERENCES optimization_qualified_inputs(id),
    cost_id UUID NOT NULL REFERENCES shipment_costs(id),
    ledger_version BIGINT NOT NULL CHECK (ledger_version >= 0),
    category VARCHAR(40) NOT NULL CHECK (category IN ('FUEL','DRIVER','TOLL','ACCESSORIAL','PERMIT')),
    amount NUMERIC NOT NULL CHECK (amount >= 0 AND amount NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric)),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (qualified_input_id,cost_id)
);
CREATE TRIGGER optimization_qualified_cost_immutable BEFORE UPDATE OR DELETE ON optimization_qualified_forecast_costs
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_immutable_audit();

CREATE FUNCTION guard_optimization_qualified_input() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE prior optimization_qualified_inputs%ROWTYPE;
DECLARE registered JSONB;
DECLARE row_source JSONB;
DECLARE cost shipment_costs%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'optimization_qualified_inputs' THEN
        IF NOT EXISTS (SELECT 1 FROM trip_stops WHERE load_id = NEW.load_id AND trip_id = NEW.trip_id) THEN
            RAISE EXCEPTION 'Qualified input must reference the actual Load/Trip context' USING ERRCODE = '23514';
        END IF;
        SELECT eligibility_source_policy #> ARRAY['qualifiedSources',NEW.input_kind] INTO registered
            FROM optimization_policy_versions WHERE id = NEW.policy_id;
        row_source := jsonb_build_object('type',NEW.source_type,'reference',NEW.source_reference,'version',NEW.source_version,'classification',NEW.source_class);
        IF registered IS NULL OR NOT registered @> jsonb_build_array(row_source) THEN
            RAISE EXCEPTION 'Qualified input requires explicit registered source and version' USING ERRCODE = '23514';
        END IF;
        IF NEW.supersedes_input_id IS NULL THEN
            IF NEW.evidence_version <> 1 THEN RAISE EXCEPTION 'Initial evidence version must be one' USING ERRCODE = '23514'; END IF;
        ELSE
            SELECT * INTO prior FROM optimization_qualified_inputs WHERE id = NEW.supersedes_input_id FOR UPDATE;
            IF prior.load_id IS DISTINCT FROM NEW.load_id OR prior.trip_id IS DISTINCT FROM NEW.trip_id
                OR prior.driver_id IS DISTINCT FROM NEW.driver_id OR prior.truck_id IS DISTINCT FROM NEW.truck_id
                OR prior.input_kind IS DISTINCT FROM NEW.input_kind OR NEW.evidence_version <> prior.evidence_version + 1
                OR NEW.captured_at < prior.captured_at THEN
                RAISE EXCEPTION 'Source correction must preserve context and append a version' USING ERRCODE = '23514';
            END IF;
        END IF;
    ELSE
        SELECT * INTO prior FROM optimization_qualified_inputs WHERE id = NEW.qualified_input_id;
        SELECT * INTO cost FROM shipment_costs WHERE id = NEW.cost_id FOR UPDATE;
        IF prior.input_kind IS DISTINCT FROM 'FORECAST_COST' OR cost.load_id IS DISTINCT FROM prior.load_id
            OR (cost.trip_id IS NOT NULL AND cost.trip_id <> prior.trip_id)
            OR (cost.driver_id IS NOT NULL AND cost.driver_id <> prior.driver_id)
            OR (cost.truck_id IS NOT NULL AND cost.truck_id <> prior.truck_id)
            OR cost.cost_basis IS DISTINCT FROM 'ESTIMATE' OR cost.status IS DISTINCT FROM 'APPROVED'
            OR cost.approved_by IS NULL OR cost.approved_at IS NULL
            OR cost.version IS DISTINCT FROM NEW.ledger_version OR cost.category IS DISTINCT FROM NEW.category
            OR cost.amount IS DISTINCT FROM NEW.amount OR cost.currency IS DISTINCT FROM NEW.currency
            OR prior.unit IS DISTINCT FROM NEW.currency THEN
            RAISE EXCEPTION 'Only exact approved candidate-specific estimate ledger facts may be qualified' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER optimization_qualified_input_insert BEFORE INSERT ON optimization_qualified_inputs
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_qualified_input();
CREATE TRIGGER optimization_qualified_cost_insert BEFORE INSERT ON optimization_qualified_forecast_costs
    FOR EACH ROW EXECUTE FUNCTION guard_optimization_qualified_input();

-- Parent and all ledger references must commit together. A half-captured forecast is not usable evidence.
CREATE FUNCTION guard_optimization_qualified_forecast_complete() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE evidence optimization_qualified_inputs%ROWTYPE;
DECLARE forecast JSONB;
DECLARE item JSONB;
DECLARE required TEXT[] := ARRAY['FUEL','DRIVER','TOLL'];
DECLARE required_category TEXT;
DECLARE links BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'optimization_qualified_inputs' THEN
        SELECT * INTO evidence FROM optimization_qualified_inputs WHERE id = NEW.id;
    ELSE
        SELECT * INTO evidence FROM optimization_qualified_inputs WHERE id = NEW.qualified_input_id;
    END IF;
    IF evidence.input_kind <> 'FORECAST_COST' THEN RETURN NULL; END IF;
    forecast := evidence.payload -> 'forecast';
    IF jsonb_typeof(forecast -> 'costs') IS DISTINCT FROM 'array'
        OR jsonb_typeof(forecast -> 'accessorialApplicable') IS DISTINCT FROM 'boolean'
        OR jsonb_typeof(forecast -> 'permitApplicable') IS DISTINCT FROM 'boolean'
        OR evidence.payload -> 'capacity' IS DISTINCT FROM 'null'::jsonb
        OR evidence.payload -> 'qualification' IS DISTINCT FROM 'null'::jsonb THEN
        RAISE EXCEPTION 'Explicit complete forecast payload and conditional applicability required' USING ERRCODE = '23514';
    END IF;
    IF (forecast ->> 'accessorialApplicable')::boolean THEN required := array_append(required,'ACCESSORIAL'); END IF;
    IF (forecast ->> 'permitApplicable')::boolean THEN required := array_append(required,'PERMIT'); END IF;
    SELECT count(*) INTO links FROM optimization_qualified_forecast_costs WHERE qualified_input_id = evidence.id;
    IF links <> jsonb_array_length(forecast -> 'costs') THEN
        RAISE EXCEPTION 'All frozen forecast lines must have exact ledger references' USING ERRCODE = '23514';
    END IF;
    FOREACH required_category IN ARRAY required LOOP
        IF NOT EXISTS (SELECT 1 FROM optimization_qualified_forecast_costs WHERE qualified_input_id=evidence.id AND category=required_category) THEN
            RAISE EXCEPTION 'Required forecast category is missing' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    FOR item IN SELECT value FROM jsonb_array_elements(forecast -> 'costs') LOOP
        IF NOT EXISTS (SELECT 1 FROM optimization_qualified_forecast_costs l WHERE l.qualified_input_id=evidence.id
            AND l.cost_id=(item ->> 'costId')::uuid AND l.ledger_version=(item ->> 'ledgerVersion')::bigint
            AND l.category=item ->> 'category' AND l.amount=(item ->> 'amount')::numeric AND l.currency=item ->> 'currency')
            OR NOT (item ->> 'category' = ANY(required))
            OR item ->> 'costBasis' IS DISTINCT FROM 'ESTIMATE' OR item ->> 'status' IS DISTINCT FROM 'APPROVED'
            OR item ->> 'approvedBy' IS NULL OR item ->> 'approvedAt' IS NULL
            OR length(btrim(coalesce(item ->> 'forecastPolicyCode',''))) = 0
            OR coalesce((item ->> 'forecastPolicyVersion')::integer,0) <= 0 THEN
            RAISE EXCEPTION 'Frozen forecast line must reconcile with approved variable ledger input' USING ERRCODE = '23514';
        END IF;
        IF (item ->> 'amount')::numeric = 0 AND (item #>> '{zeroEvidence,reasonCode}' IS DISTINCT FROM 'ZERO_COST_CONFIRMED'
            OR length(btrim(coalesce(item #>> '{zeroEvidence,reason}',''))) = 0
            OR item #>> '{zeroEvidence,confirmedBy}' IS DISTINCT FROM evidence.captured_by::text
            OR (item #>> '{zeroEvidence,confirmedAt}')::timestamptz IS DISTINCT FROM evidence.captured_at) THEN
            RAISE EXCEPTION 'Zero forecast requires explicit source capture actor/time/reason evidence' USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER optimization_qualified_forecast_complete AFTER INSERT ON optimization_qualified_inputs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION guard_optimization_qualified_forecast_complete();
CREATE CONSTRAINT TRIGGER optimization_qualified_forecast_link_complete AFTER INSERT ON optimization_qualified_forecast_costs
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION guard_optimization_qualified_forecast_complete();
