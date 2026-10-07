CREATE TABLE contract_load_rating_mileage (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL REFERENCES loads(id),
    component_type VARCHAR(40) NOT NULL CHECK (component_type IN ('LINEHAUL', 'FSC', 'RATE_TIER')),
    contract_id UUID NOT NULL,
    contract_version INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    original_value NUMERIC NOT NULL,
    original_unit VARCHAR(20) NOT NULL CHECK (original_unit = 'MILE'),
    normalized_miles NUMERIC(12,3) NOT NULL,
    provenance TEXT NOT NULL CHECK (length(btrim(provenance)) > 0),
    captured_by UUID NOT NULL REFERENCES employees(id),
    captured_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (contract_id, contract_version, currency)
        REFERENCES customer_rating_contract_versions(contract_id, contract_version, currency),
    CHECK (original_value >= 0 AND original_value <= 999999999.999
        AND original_value = normalized_miles)
);
CREATE INDEX ix_contract_load_rating_mileage_load ON contract_load_rating_mileage(load_id);

CREATE FUNCTION guard_contract_load_rating_mileage() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Contract rating mileage evidence is immutable; capture new evidence'
        USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER immutable_contract_load_rating_mileage
    BEFORE UPDATE OR DELETE ON contract_load_rating_mileage
    FOR EACH ROW EXECUTE FUNCTION guard_contract_load_rating_mileage();

CREATE FUNCTION validate_contract_load_rating_mileage() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE c customer_rating_contract_versions%ROWTYPE;
DECLARE l loads%ROWTYPE;
BEGIN
    SELECT * INTO c FROM customer_rating_contract_versions
        WHERE contract_id = NEW.contract_id AND contract_version = NEW.contract_version;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Contract mileage requires a published contract version' USING ERRCODE = '23514';
    END IF;
    SELECT * INTO l FROM loads WHERE id = NEW.load_id;
    IF NOT FOUND OR l.customer_id <> c.customer_id OR NEW.currency <> c.currency
        OR l.requested_pickup_business_date IS NULL
        OR l.requested_pickup_business_date < c.effective_from
        OR (c.effective_to IS NOT NULL AND l.requested_pickup_business_date > c.effective_to) THEN
        RAISE EXCEPTION 'Contract mileage requires Load customer/currency/effective business date context'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER contract_load_rating_mileage_context
    BEFORE INSERT ON contract_load_rating_mileage
    FOR EACH ROW EXECUTE FUNCTION validate_contract_load_rating_mileage();
