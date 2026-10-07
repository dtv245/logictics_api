-- Additive financial history protection. Legacy rows are not reclassified or rehashed.
ALTER TABLE payments ADD COLUMN input_hash_version SMALLINT;

CREATE TABLE payment_command_events (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    payment_version BIGINT NOT NULL,
    command_type TEXT NOT NULL CHECK (command_type IN ('CREATE','METADATA','CANCEL')),
    previous_status TEXT,
    new_status TEXT NOT NULL,
    previous_description TEXT,
    new_description TEXT,
    previous_reference_number TEXT,
    new_reference_number TEXT,
    actor_id UUID NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    reason TEXT,
    UNIQUE (payment_id,payment_version),
    CHECK (command_type <> 'CANCEL' OR (reason IS NOT NULL AND length(btrim(reason)) BETWEEN 1 AND 1000))
);

CREATE FUNCTION guard_payment_command_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'PAYMENT_DELETE_FORBIDDEN: financial history must be preserved' USING ERRCODE='23514';
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'PENDING' OR NEW.invoice_id IS NULL OR NEW.amount_amount <= 0
           OR NEW.amount_currency !~ '^[A-Z]{3}$' OR NEW.idempotency_key IS NULL
           OR length(btrim(NEW.idempotency_key)) NOT BETWEEN 1 AND 200
           OR NEW.idempotency_key <> btrim(NEW.idempotency_key)
           OR NEW.input_hash IS NULL OR NEW.input_hash !~ '^[0-9a-f]{64}$'
           OR NEW.input_hash_version IS DISTINCT FROM 1 OR NEW.version IS DISTINCT FROM 0
           OR NEW.recorded_by_user_id IS NULL OR NEW.recorded_at IS NULL THEN
            RAISE EXCEPTION 'PAYMENT_COMMAND_INVALID: pending financial command identity required' USING ERRCODE='23514';
        END IF;
    ELSE
        IF ROW(NEW.id,NEW.invoice_id,NEW.amount_amount,NEW.amount_currency,NEW.idempotency_key,
               NEW.input_hash,NEW.input_hash_version,NEW.tenant_id,NEW.recorded_by_user_id,NEW.recorded_at,
               NEW.stripe_payment_method_id,NEW.stripe_payment_intent_id,
               NEW.billing_address_line1,NEW.billing_address_line2,NEW.billing_address_city,
               NEW.billing_address_state,NEW.billing_address_zip_code,NEW.billing_address_country,
               NEW.created_at,NEW.created_by)
           IS DISTINCT FROM
           ROW(OLD.id,OLD.invoice_id,OLD.amount_amount,OLD.amount_currency,OLD.idempotency_key,
               OLD.input_hash,OLD.input_hash_version,OLD.tenant_id,OLD.recorded_by_user_id,OLD.recorded_at,
               OLD.stripe_payment_method_id,OLD.stripe_payment_intent_id,
               OLD.billing_address_line1,OLD.billing_address_line2,OLD.billing_address_city,
               OLD.billing_address_state,OLD.billing_address_zip_code,OLD.billing_address_country,
               OLD.created_at,OLD.created_by) THEN
            RAISE EXCEPTION 'PAYMENT_FINANCIAL_FIELDS_IMMUTABLE' USING ERRCODE='23514';
        END IF;
        IF upper(btrim(OLD.status)) <> 'PENDING' THEN
            RAISE EXCEPTION 'PAYMENT_TERMINAL_HISTORY_IMMUTABLE' USING ERRCODE='23514';
        END IF;
        IF NEW.status IS DISTINCT FROM OLD.status AND NEW.status <> 'CANCELLED' THEN
            RAISE EXCEPTION 'PAYMENT_CANCEL_STATE_CONFLICT' USING ERRCODE='23514';
        END IF;
        IF NEW.version IS DISTINCT FROM OLD.version + 1 THEN
            RAISE EXCEPTION 'PAYMENT_VERSION_REQUIRED: mutation must advance exactly one version' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payment_command_state_guard BEFORE INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION guard_payment_command_state();

CREATE FUNCTION guard_payment_event_history() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE p payments%ROWTYPE;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'PAYMENT_AUDIT_IMMUTABLE' USING ERRCODE='23514';
    END IF;
    SELECT * INTO p FROM payments WHERE id=NEW.payment_id;
    IF NOT FOUND OR NEW.payment_version IS DISTINCT FROM p.version
       OR ROW(NEW.new_status,NEW.new_description,NEW.new_reference_number)
          IS DISTINCT FROM ROW(p.status,p.description,p.reference_number)
       OR (NEW.command_type='CREATE' AND NEW.actor_id IS DISTINCT FROM p.recorded_by_user_id) THEN
        RAISE EXCEPTION 'PAYMENT_AUDIT_STATE_MISMATCH' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER payment_event_history_guard BEFORE INSERT OR UPDATE OR DELETE ON payment_command_events
    FOR EACH ROW EXECUTE FUNCTION guard_payment_event_history();

CREATE FUNCTION require_payment_command_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE e payment_command_events%ROWTYPE;
BEGIN
    SELECT * INTO e FROM payment_command_events WHERE payment_id=NEW.id AND payment_version=NEW.version;
    IF NOT FOUND OR ROW(e.new_status,e.new_description,e.new_reference_number)
       IS DISTINCT FROM ROW(NEW.status,NEW.description,NEW.reference_number) THEN
        RAISE EXCEPTION 'PAYMENT_AUDIT_REQUIRED' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        IF e.command_type <> 'CREATE' OR e.previous_status IS NOT NULL
           OR e.previous_description IS NOT NULL OR e.previous_reference_number IS NOT NULL
           OR e.actor_id IS DISTINCT FROM NEW.recorded_by_user_id THEN
            RAISE EXCEPTION 'PAYMENT_CREATION_AUDIT_REQUIRED' USING ERRCODE='23514';
        END IF;
    ELSE
        IF ROW(e.previous_status,e.previous_description,e.previous_reference_number)
           IS DISTINCT FROM ROW(OLD.status,OLD.description,OLD.reference_number)
           OR e.command_type IS DISTINCT FROM (CASE WHEN NEW.status IS DISTINCT FROM OLD.status THEN 'CANCEL' ELSE 'METADATA' END) THEN
            RAISE EXCEPTION 'PAYMENT_MUTATION_AUDIT_REQUIRED' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER payment_command_evidence_guard AFTER INSERT OR UPDATE ON payments
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_payment_command_evidence();
