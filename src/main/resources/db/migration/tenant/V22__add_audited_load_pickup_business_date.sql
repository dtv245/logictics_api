-- Do not derive or backfill business dates from legacy TIMESTAMPTZ.
-- Existing requested_pickup_date remains the instant/appointment contract.
ALTER TABLE loads ADD COLUMN requested_pickup_business_date DATE;
ALTER TABLE loads ADD COLUMN pickup_business_date_change_id UUID;

CREATE TABLE load_pickup_business_date_changes (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL REFERENCES loads(id),
    previous_change_id UUID REFERENCES load_pickup_business_date_changes(id),
    previous_value DATE,
    value DATE NOT NULL,
    actor_id UUID NOT NULL REFERENCES employees(id),
    corrected_at TIMESTAMPTZ NOT NULL,
    reason_code VARCHAR(80) NOT NULL CHECK (length(btrim(reason_code)) > 0),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
    provenance TEXT NOT NULL CHECK (length(btrim(provenance)) > 0),
    UNIQUE (load_id, id),
    UNIQUE NULLS NOT DISTINCT (load_id, previous_change_id)
);

ALTER TABLE loads ADD CONSTRAINT fk_load_pickup_date_change
    FOREIGN KEY (id, pickup_business_date_change_id)
    REFERENCES load_pickup_business_date_changes(load_id, id);
ALTER TABLE loads ADD CONSTRAINT ck_load_pickup_date_audit
    CHECK ((requested_pickup_business_date IS NULL) = (pickup_business_date_change_id IS NULL));

CREATE FUNCTION guard_load_pickup_business_date_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Pickup business-date correction history is immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_load_pickup_business_date_changes
    BEFORE UPDATE OR DELETE ON load_pickup_business_date_changes
    FOR EACH ROW EXECUTE FUNCTION guard_load_pickup_business_date_history();

CREATE FUNCTION validate_load_pickup_business_date_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE evidence load_pickup_business_date_changes%ROWTYPE;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.requested_pickup_business_date IS NOT NULL OR NEW.pickup_business_date_change_id IS NOT NULL THEN
            RAISE EXCEPTION 'Create Load first, then record its explicit business-date audit' USING ERRCODE = '23514';
        END IF;
    ELSIF NEW.requested_pickup_business_date IS DISTINCT FROM OLD.requested_pickup_business_date
        OR NEW.pickup_business_date_change_id IS DISTINCT FROM OLD.pickup_business_date_change_id THEN
        SELECT * INTO evidence FROM load_pickup_business_date_changes
            WHERE id = NEW.pickup_business_date_change_id AND load_id = NEW.id;
        IF NOT FOUND OR evidence.value IS DISTINCT FROM NEW.requested_pickup_business_date
            OR evidence.previous_value IS DISTINCT FROM OLD.requested_pickup_business_date
            OR evidence.previous_change_id IS DISTINCT FROM OLD.pickup_business_date_change_id
            OR NEW.pickup_business_date_change_id IS NOT DISTINCT FROM OLD.pickup_business_date_change_id THEN
            RAISE EXCEPTION 'Explicit pickup business-date change requires its own chained audit' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER audited_load_pickup_business_date
    BEFORE INSERT OR UPDATE ON loads
    FOR EACH ROW EXECUTE FUNCTION validate_load_pickup_business_date_change();
