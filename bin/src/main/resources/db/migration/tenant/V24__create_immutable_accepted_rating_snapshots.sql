CREATE TABLE accepted_rating_snapshots (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL REFERENCES loads(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    pricing_date DATE NOT NULL,
    pricing_date_source VARCHAR(60) NOT NULL CHECK (pricing_date_source = 'LOAD_REQUESTED_PICKUP_DATE'),
    pricing_date_change_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    rule_version INTEGER NOT NULL,
    rounding_policy_code VARCHAR(60) NOT NULL CHECK (rounding_policy_code = 'RatingPolicyV1'),
    rounding_policy_version INTEGER NOT NULL CHECK (rounding_policy_version = 1),
    subtotal NUMERIC NOT NULL CHECK (subtotal >= 0 AND subtotal NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)),
    calculation JSONB NOT NULL CHECK (jsonb_typeof(calculation) = 'object'),
    input_hash VARCHAR(64) NOT NULL CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    result_hash VARCHAR(64) NOT NULL CHECK (result_hash ~ '^[0-9a-f]{64}$'),
    accepted_by UUID NOT NULL REFERENCES employees(id),
    accepted_at TIMESTAMPTZ NOT NULL,
    supersedes_snapshot_id UUID REFERENCES accepted_rating_snapshots(id),
    reason_code VARCHAR(80),
    reason TEXT,
    operation VARCHAR(40) NOT NULL CHECK (operation = 'RATING_ACCEPT'),
    idempotency_key VARCHAR(200) NOT NULL CHECK (length(btrim(idempotency_key)) > 0),
    command_hash VARCHAR(64) NOT NULL CHECK (command_hash ~ '^[0-9a-f]{64}$'),
    UNIQUE (operation, idempotency_key),
    UNIQUE (id, load_id, customer_id, currency),
    FOREIGN KEY (load_id, pricing_date_change_id) REFERENCES load_pickup_business_date_changes(load_id, id),
    FOREIGN KEY (rule_id, rule_version) REFERENCES customer_rate_rule_versions(rule_id, rule_version),
    CHECK ((supersedes_snapshot_id IS NULL AND reason_code IS NULL AND reason IS NULL)
        OR (supersedes_snapshot_id IS NOT NULL AND reason_code IS NOT NULL AND reason_code ~ '^[A-Z][A-Z0-9_]{0,79}$'
            AND reason IS NOT NULL AND length(btrim(reason)) > 0))
);
CREATE INDEX ix_accepted_rating_snapshots_load ON accepted_rating_snapshots(load_id);
CREATE INDEX ix_accepted_rating_snapshots_supersedes ON accepted_rating_snapshots(supersedes_snapshot_id);

CREATE FUNCTION guard_accepted_rating_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE l loads%ROWTYPE;
DECLARE r customer_rate_rule_versions%ROWTYPE;
DECLARE prior accepted_rating_snapshots%ROWTYPE;
DECLARE item JSONB;
DECLARE total NUMERIC := 0;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Accepted rating snapshot is immutable; create a superseding snapshot' USING ERRCODE = '23514';
    END IF;
    SELECT * INTO l FROM loads WHERE id = NEW.load_id FOR UPDATE;
    SELECT * INTO r FROM customer_rate_rule_versions WHERE rule_id = NEW.rule_id AND rule_version = NEW.rule_version;
    IF l.customer_id IS DISTINCT FROM NEW.customer_id OR l.requested_pickup_business_date IS DISTINCT FROM NEW.pricing_date
        OR l.pickup_business_date_change_id IS DISTINCT FROM NEW.pricing_date_change_id
        OR r.currency IS DISTINCT FROM NEW.currency OR NEW.pricing_date < r.effective_from
        OR (r.effective_to IS NOT NULL AND NEW.pricing_date > r.effective_to) THEN
        RAISE EXCEPTION 'Accepted rating context is stale or incompatible' USING ERRCODE = '23514';
    END IF;
    IF NEW.supersedes_snapshot_id IS NOT NULL THEN
        SELECT * INTO prior FROM accepted_rating_snapshots WHERE id = NEW.supersedes_snapshot_id;
        IF prior.load_id IS DISTINCT FROM NEW.load_id OR prior.customer_id IS DISTINCT FROM NEW.customer_id
            OR prior.currency IS DISTINCT FROM NEW.currency THEN
            RAISE EXCEPTION 'Rating correction must retain Load/customer/currency identity' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF NEW.calculation #>> '{inputs,loadId}' IS DISTINCT FROM NEW.load_id::text
        OR NEW.calculation #>> '{inputs,matchContext,customerId}' IS DISTINCT FROM NEW.customer_id::text
        OR NEW.calculation #>> '{inputs,pricingDate,pricingDate}' IS DISTINCT FROM NEW.pricing_date::text
        OR NEW.calculation #>> '{inputs,pricingDate,pricingDateSource}' IS DISTINCT FROM NEW.pricing_date_source
        OR NEW.calculation #>> '{inputs,pricingDate,sourceChangeId}' IS DISTINCT FROM NEW.pricing_date_change_id::text
        OR NEW.calculation #>> '{inputs,rule,ruleId}' IS DISTINCT FROM NEW.rule_id::text
        OR (NEW.calculation #>> '{inputs,rule,version}')::integer IS DISTINCT FROM NEW.rule_version
        OR NEW.calculation ->> 'currency' IS DISTINCT FROM NEW.currency
        OR (NEW.calculation ->> 'subtotal')::numeric IS DISTINCT FROM NEW.subtotal
        OR NEW.calculation ->> 'inputHash' IS DISTINCT FROM NEW.input_hash
        OR NEW.calculation ->> 'resultHash' IS DISTINCT FROM NEW.result_hash
        OR NEW.calculation ->> 'roundingPolicyCode' IS DISTINCT FROM NEW.rounding_policy_code
        OR (NEW.calculation ->> 'roundingPolicyVersion')::integer IS DISTINCT FROM NEW.rounding_policy_version
        OR jsonb_typeof(NEW.calculation -> 'lines') IS DISTINCT FROM 'array'
        OR jsonb_array_length(NEW.calculation -> 'lines') = 0 THEN
        RAISE EXCEPTION 'Rating payload must retain exact date/source/rule/result identity' USING ERRCODE = '23514';
    END IF;
    FOR item IN SELECT value FROM jsonb_array_elements(NEW.calculation -> 'lines') LOOP
        IF item ->> 'currency' IS DISTINCT FROM NEW.currency OR item ->> 'amount' IS NULL
            OR (item ->> 'amount')::numeric < 0 THEN
            RAISE EXCEPTION 'Rating lines must have compatible explicit amounts' USING ERRCODE = '23514';
        END IF;
        total := total + (item ->> 'amount')::numeric;
    END LOOP;
    IF total IS DISTINCT FROM NEW.subtotal THEN
        RAISE EXCEPTION 'Rating subtotal must equal rounded money lines' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER accepted_rating_snapshot_guard BEFORE INSERT OR UPDATE OR DELETE ON accepted_rating_snapshots
    FOR EACH ROW EXECUTE FUNCTION guard_accepted_rating_snapshot();
