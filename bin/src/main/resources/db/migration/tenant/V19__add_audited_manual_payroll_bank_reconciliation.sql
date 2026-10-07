-- Manual bank evidence must have one immutable claim per bank transaction.
CREATE UNIQUE INDEX uq_payroll_bank_transaction
    ON payroll_payment_events ((verification_json->>'transactionReference'))
    WHERE source_type = 'BANK' AND status = 'APPLIED';

CREATE FUNCTION validate_manual_bank_event() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    payment_row payroll_payments%ROWTYPE;
    case_row payroll_payment_events%ROWTYPE;
BEGIN
    IF NEW.source_type <> 'BANK' THEN
        RETURN NEW;
    END IF;

    SELECT * INTO payment_row
      FROM payroll_payments
     WHERE id = NEW.payment_id;

    SELECT * INTO case_row
      FROM payroll_payment_events
     WHERE id = NEW.resolves_event_id;

    IF payment_row.id IS NULL
       OR NEW.amount IS DISTINCT FROM payment_row.amount
       OR NEW.currency IS DISTINCT FROM payment_row.currency THEN
        RAISE EXCEPTION 'Manual bank evidence must match payment amount and currency';
    END IF;

    IF NEW.status <> 'APPLIED'
       OR NEW.source_key IS DISTINCT FROM NEW.verification_json->>'idempotencyKey'
       OR NEW.actor_id IS NULL
       OR NEW.actor_id::text IS DISTINCT FROM NEW.verification_json->>'actorId'
       OR NEW.provider_key IS NOT NULL
       OR NEW.resolves_event_id IS NULL
       OR COALESCE(NEW.verification_json->>'bankSource', '') = ''
       OR COALESCE(NEW.verification_json->>'transactionReference', '') = ''
       OR NEW.provider_reference IS DISTINCT FROM NEW.verification_json->>'transactionReference'
       OR COALESCE(NEW.verification_json->>'counterpartyReference', '') = ''
       OR COALESCE(NEW.verification_json->>'evidenceReference', '') = ''
       OR COALESCE(NEW.verification_json->>'reason', '') = '' THEN
        RAISE EXCEPTION 'Manual bank evidence identity and attestation are required';
    END IF;

    IF payment_row.payment_method <> 'BANK_TRANSFER'
       OR payment_row.destination_reference IS NULL
       OR NEW.verification_json->>'counterpartyReference' IS DISTINCT FROM payment_row.destination_reference THEN
        RAISE EXCEPTION 'Manual bank counterparty does not match scheduled bank destination';
    END IF;

    IF case_row.id IS NULL
       OR case_row.payment_id IS DISTINCT FROM NEW.payment_id
       OR case_row.status <> 'RECONCILIATION_REQUIRED'
       OR case_row.amount IS DISTINCT FROM NEW.amount
       OR case_row.currency IS DISTINCT FROM NEW.currency
       OR EXISTS (
            SELECT 1 FROM payroll_payment_events resolver
             WHERE resolver.resolves_event_id = case_row.id
               AND resolver.status = 'APPLIED') THEN
        RAISE EXCEPTION 'Manual bank evidence must resolve one open matching case';
    END IF;

    IF NEW.outcome NOT IN ('SUCCEEDED', 'FAILED')
       OR NEW.occurred_at IS NULL THEN
        RAISE EXCEPTION 'Manual bank outcome and transaction time are required';
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER validate_manual_bank_event
    BEFORE INSERT ON payroll_payment_events
    FOR EACH ROW EXECUTE FUNCTION validate_manual_bank_event();

CREATE OR REPLACE FUNCTION require_payroll_success_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'SUCCEEDED' AND OLD.status <> 'SUCCEEDED' AND NOT EXISTS (
        SELECT 1
          FROM payroll_payment_events e
         WHERE e.payment_id = NEW.id
           AND e.outcome = 'SUCCEEDED'
           AND e.status = 'APPLIED'
           AND e.amount = NEW.amount
           AND e.currency = NEW.currency
           AND (
                (e.source_type = 'PROVIDER'
                 AND e.provider_key = NEW.provider_key
                 AND e.provider_reference IS NOT DISTINCT FROM NEW.provider_reference)
                OR
                (e.source_type = 'BANK'
                 AND e.resolves_event_id IS NOT NULL
                 AND e.provider_reference = NEW.provider_reference)
           )
    ) THEN
        RAISE EXCEPTION 'Payment success requires verified matching provider or resolved bank evidence';
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION protect_payroll_payment_attempt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payroll payment attempts are immutable history';
    END IF;

    IF ROW(OLD.id, OLD.payroll_run_item_id, OLD.attempt_number, OLD.idempotency_key, OLD.payment_method,
           OLD.amount, OLD.currency, OLD.provider_key, OLD.destination_reference, OLD.request_snapshot_json,
           OLD.scheduled_at, OLD.scheduled_by, OLD.created_at)
       IS DISTINCT FROM
       ROW(NEW.id, NEW.payroll_run_item_id, NEW.attempt_number, NEW.idempotency_key, NEW.payment_method,
           NEW.amount, NEW.currency, NEW.provider_key, NEW.destination_reference, NEW.request_snapshot_json,
           NEW.scheduled_at, NEW.scheduled_by, NEW.created_at) THEN
        RAISE EXCEPTION 'Payroll payment financial inputs are immutable';
    END IF;

    IF OLD.provider_reference IS NOT NULL
       AND OLD.provider_reference IS DISTINCT FROM NEW.provider_reference
       AND NOT EXISTS (
            SELECT 1 FROM payroll_payment_events e
             WHERE e.payment_id = OLD.id
               AND e.source_type = 'BANK'
               AND e.status = 'APPLIED'
               AND e.provider_reference = NEW.provider_reference
               AND e.resolves_event_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Payroll payment reference cannot change without resolved bank evidence';
    END IF;

    IF NEW.status <> OLD.status AND NOT (
        (OLD.status = 'SCHEDULED' AND NEW.status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
        OR (OLD.status IN ('SUBMITTED', 'PROCESSING') AND NEW.status IN ('SUCCEEDED', 'FAILED', 'RECONCILIATION_REQUIRED'))
        OR (OLD.status = 'FAILED' AND NEW.status IN ('RECONCILIATION_REQUIRED', 'SUCCEEDED'))
    ) THEN
        RAISE EXCEPTION 'Payroll payment workflow invalid';
    END IF;

    IF OLD.status = 'FAILED' AND NEW.status = 'SUCCEEDED' AND NOT EXISTS (
        SELECT 1 FROM payroll_payment_events e
         WHERE e.payment_id = OLD.id
           AND e.source_type = 'BANK'
           AND e.status = 'APPLIED'
           AND e.outcome = 'SUCCEEDED'
           AND e.resolves_event_id IS NOT NULL
           AND e.amount = OLD.amount
           AND e.currency = OLD.currency
           AND e.provider_reference = NEW.provider_reference) THEN
        RAISE EXCEPTION 'Failed payment can succeed only with matching resolved bank evidence';
    END IF;

    IF OLD.status = 'SUCCEEDED' AND to_jsonb(OLD) IS DISTINCT FROM to_jsonb(NEW) THEN
        RAISE EXCEPTION 'Successful payment evidence is immutable';
    END IF;
    RETURN NEW;
END $$;

-- A late, explicitly reconciled bank success may settle a failed item without
-- reopening or rewriting its locked financial inputs.
CREATE OR REPLACE FUNCTION protect_locked_payroll_item() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE parent_status VARCHAR;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        SELECT status INTO parent_status FROM payroll_runs WHERE id = OLD.payroll_run_id FOR UPDATE;
        IF parent_status IN ('LOCKED', 'PAYMENT_SCHEDULED', 'PAID') THEN
            IF TG_OP = 'DELETE' THEN
                RAISE EXCEPTION 'Locked payroll item cannot be deleted';
            END IF;
            IF (to_jsonb(OLD) - ARRAY['status', 'version']) IS DISTINCT FROM
               (to_jsonb(NEW) - ARRAY['status', 'version']) THEN
                RAISE EXCEPTION 'Locked payroll item money and snapshot are immutable';
            END IF;
            IF NEW.status <> OLD.status AND NOT (
                (OLD.status IN ('CALCULATED', 'SCHEDULED', 'PAYMENT_FAILED') AND NEW.status = 'PAYMENT_PENDING')
                OR (OLD.status = 'PAYMENT_PENDING' AND NEW.status IN ('PAID', 'PAYMENT_FAILED'))
                OR (OLD.status = 'PAYMENT_FAILED' AND NEW.status = 'PAID' AND EXISTS (
                    SELECT 1
                      FROM payroll_payments p
                      JOIN payroll_payment_events e ON e.payment_id = p.id
                     WHERE p.payroll_run_item_id = OLD.id
                       AND p.status = 'SUCCEEDED'
                       AND e.source_type = 'BANK'
                       AND e.status = 'APPLIED'
                       AND e.outcome = 'SUCCEEDED'
                       AND e.resolves_event_id IS NOT NULL
                       AND e.amount = p.amount
                       AND e.currency = p.currency
                       AND e.provider_reference = p.provider_reference
                ))
            ) THEN
                RAISE EXCEPTION 'Payroll item payment workflow invalid';
            END IF;
        END IF;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        SELECT status INTO parent_status FROM payroll_runs WHERE id = NEW.payroll_run_id FOR UPDATE;
        IF parent_status IN ('LOCKED', 'PAYMENT_SCHEDULED', 'PAID') AND
           (TG_OP = 'INSERT' OR NEW.payroll_run_id <> OLD.payroll_run_id) THEN
            RAISE EXCEPTION 'Cannot attach item to locked payroll';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
