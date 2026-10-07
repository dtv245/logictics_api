-- V28 has already applied: forward-only driver snapshot history hardening.
CREATE FUNCTION protect_driver_settlement_calculation_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.entity_type='DRIVER_SETTLEMENT' THEN
        RAISE EXCEPTION 'Driver settlement calculation history is immutable; append an audited snapshot' USING ERRCODE='23514';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_driver_settlement_calculation_snapshot BEFORE UPDATE OR DELETE ON calculation_snapshots
    FOR EACH ROW EXECUTE FUNCTION protect_driver_settlement_calculation_snapshot();
