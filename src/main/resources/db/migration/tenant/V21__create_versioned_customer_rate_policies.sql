-- Rating V1 only; database-per-tenant (ADR-001). No business input defaults.
-- Identity rows serialize version append; published versions are immutable.
CREATE TABLE customer_rating_contracts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    created_by UUID NOT NULL REFERENCES employees(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, customer_id)
);

CREATE TABLE customer_rating_contract_versions (
    contract_id UUID NOT NULL,
    contract_version INTEGER NOT NULL CHECK (contract_version > 0),
    customer_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by UUID NOT NULL REFERENCES employees(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (contract_id, contract_version),
    FOREIGN KEY (contract_id, customer_id) REFERENCES customer_rating_contracts(id, customer_id),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    UNIQUE (contract_id, contract_version, currency)
);

CREATE TABLE customer_rate_rules (
    id UUID PRIMARY KEY,
    created_by UUID NOT NULL REFERENCES employees(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_rate_rule_versions (
    rule_id UUID NOT NULL REFERENCES customer_rate_rules(id),
    rule_version INTEGER NOT NULL CHECK (rule_version > 0),
    priority INTEGER NOT NULL,
    customer_id UUID REFERENCES customers(id),
    contract_id UUID,
    contract_version INTEGER,
    lane VARCHAR(200),
    equipment VARCHAR(80),
    service VARCHAR(80),
    tier VARCHAR(80),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    effective_from DATE NOT NULL,
    effective_to DATE,
    rating_method VARCHAR(40) NOT NULL CHECK (rating_method IN ('FLAT', 'PER_MILE')),
    -- Unconstrained NUMERIC preserves authored decimal inputs without hidden rounding.
    base_rate NUMERIC NOT NULL CHECK (base_rate >= 0 AND base_rate NOT IN ('NaN'::numeric, 'Infinity'::numeric)),
    linehaul_mileage_basis VARCHAR(40),
    minimum_charge NUMERIC CHECK (minimum_charge >= 0 AND minimum_charge NOT IN ('NaN'::numeric, 'Infinity'::numeric)),
    maximum_charge NUMERIC CHECK (maximum_charge >= 0 AND maximum_charge NOT IN ('NaN'::numeric, 'Infinity'::numeric)),
    fsc_method VARCHAR(40),
    fsc_mileage_basis VARCHAR(40),
    index_provider VARCHAR(40),
    index_region VARCHAR(40),
    max_index_age_days INTEGER,
    contract_mpg NUMERIC,
    base_fuel_price NUMERIC,
    fuel_price_currency VARCHAR(3),
    fuel_price_unit VARCHAR(40),
    rounding_policy_code VARCHAR(80) NOT NULL CHECK (rounding_policy_code = 'RatingPolicyV1'),
    rounding_policy_version INTEGER NOT NULL CHECK (rounding_policy_version = 1),
    created_by UUID NOT NULL REFERENCES employees(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (rule_id, rule_version),
    FOREIGN KEY (contract_id, contract_version, currency)
        REFERENCES customer_rating_contract_versions(contract_id, contract_version, currency),
    CHECK ((contract_id IS NULL) = (contract_version IS NULL)),
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CHECK (lane IS NULL OR length(btrim(lane)) > 0),
    CHECK (equipment IS NULL OR length(btrim(equipment)) > 0),
    CHECK (service IS NULL OR length(btrim(service)) > 0),
    CHECK (tier IS NULL OR length(btrim(tier)) > 0),
    CHECK (maximum_charge IS NULL OR minimum_charge IS NULL OR maximum_charge >= minimum_charge),
    CHECK ((rating_method = 'FLAT' AND linehaul_mileage_basis IS NULL)
        OR (rating_method = 'PER_MILE' AND linehaul_mileage_basis IN
            ('CONTRACT_MILES', 'PLANNED_LOAD_MILES', 'ACTUAL_LOADED_MILES', 'ACTUAL_ALL_MILES') AND linehaul_mileage_basis IS NOT NULL)),
    CHECK (
        (fsc_method IS NULL AND fsc_mileage_basis IS NULL AND index_provider IS NULL
            AND index_region IS NULL AND max_index_age_days IS NULL AND contract_mpg IS NULL
            AND base_fuel_price IS NULL AND fuel_price_currency IS NULL AND fuel_price_unit IS NULL)
        OR
        (fsc_method = 'INDEX_BASED_MPG' AND fsc_method IS NOT NULL
            AND fsc_mileage_basis IS NOT NULL AND fsc_mileage_basis IN
                ('CONTRACT_MILES', 'PLANNED_LOAD_MILES', 'ACTUAL_LOADED_MILES', 'ACTUAL_ALL_MILES')
            AND index_provider IS NOT NULL AND index_provider = 'EIA'
            AND index_region IS NOT NULL AND index_region IN
                ('US', 'PADD1', 'PADD1A', 'PADD1B', 'PADD1C', 'PADD2', 'PADD3', 'PADD4', 'PADD5', 'CALIFORNIA')
            AND max_index_age_days IS NOT NULL AND max_index_age_days >= 0
            AND contract_mpg IS NOT NULL AND contract_mpg > 0 AND contract_mpg NOT IN ('NaN'::numeric, 'Infinity'::numeric)
            AND base_fuel_price IS NOT NULL AND base_fuel_price >= 0 AND base_fuel_price NOT IN ('NaN'::numeric, 'Infinity'::numeric)
            AND fuel_price_currency IS NOT NULL AND fuel_price_currency = 'USD'
            AND fuel_price_unit IS NOT NULL AND fuel_price_unit = 'GALLON'
            AND currency = 'USD')
    )
);

CREATE INDEX ix_customer_rate_rule_effective_priority
    ON customer_rate_rule_versions(effective_from, priority);

CREATE FUNCTION guard_customer_rating_history() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Published customer rating policy history is immutable; append a version'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER immutable_customer_rating_contracts
    BEFORE UPDATE OR DELETE ON customer_rating_contracts
    FOR EACH ROW EXECUTE FUNCTION guard_customer_rating_history();
CREATE TRIGGER immutable_customer_rating_contract_versions
    BEFORE UPDATE OR DELETE ON customer_rating_contract_versions
    FOR EACH ROW EXECUTE FUNCTION guard_customer_rating_history();
CREATE TRIGGER immutable_customer_rate_rules
    BEFORE UPDATE OR DELETE ON customer_rate_rules
    FOR EACH ROW EXECUTE FUNCTION guard_customer_rating_history();
CREATE TRIGGER immutable_customer_rate_rule_versions
    BEFORE UPDATE OR DELETE ON customer_rate_rule_versions
    FOR EACH ROW EXECUTE FUNCTION guard_customer_rating_history();

-- Contract-scoped rules cannot claim another customer or exceed the agreement period.
CREATE FUNCTION validate_customer_rate_contract() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE c customer_rating_contract_versions%ROWTYPE;
BEGIN
    IF NEW.contract_id IS NOT NULL THEN
        SELECT * INTO c FROM customer_rating_contract_versions
            WHERE contract_id = NEW.contract_id AND contract_version = NEW.contract_version;
        IF NOT FOUND OR (NEW.customer_id IS NOT NULL AND NEW.customer_id <> c.customer_id)
            OR NEW.currency <> c.currency OR NEW.effective_from < c.effective_from
            OR (c.effective_to IS NOT NULL AND (NEW.effective_to IS NULL OR NEW.effective_to > c.effective_to)) THEN
            RAISE EXCEPTION 'Customer rate rule disagrees with its versioned contract'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER customer_rate_contract_context
    BEFORE INSERT ON customer_rate_rule_versions
    FOR EACH ROW EXECUTE FUNCTION validate_customer_rate_contract();
