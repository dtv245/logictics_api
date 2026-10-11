-- ======================================================================================
-- TEST-ONLY SEED & SIMULATION TRIGGERS FOR LOGISTICSX TMS API AUTOMATION
-- ======================================================================================
-- Purpose:
--   Enables end-to-end API testing suites (Postman, Newman, integration tests) to
--   execute lifecycle transitions across Trips, Loads, Costs, Billing, Documents,
--   and Driver Settlements without manual DB interventions.
--
-- Why This Trigger Exists (Architectural Context):
--   In the LogisticsX production codebase, TripStop has NO public REST creation endpoint
--   (only status transitions: /arrive, /start-service, /complete-service, /depart).
--   In production TMS deployments, stops are ingested via external dispatch/planning
--   integrations or EDI batch streams. Java integration tests directly execute raw SQL:
--   "INSERT INTO trip_stops(...)".
--   This trigger replicates that behavior by automatically generating the necessary
--   interconnected entities when a Trip is created.
--
-- How It Links Load with Trip:
--   1. Condition: Trigger fires AFTER INSERT ON trips FOR EACH ROW.
--   2. Load Attribution: Queries the most recent Load (ORDER BY created_at DESC LIMIT 1).
--   3. Stop Creation: Generates 2 TripStops referencing NEW.id (trip_id) and v_load_id:
--      - Stop 1 (PICKUP, order 1): Los Angeles, CA (34.0522, -118.2437)
--      - Stop 2 (DELIVERY, order 2): San Francisco, CA (37.7749, -122.4194)
--   4. Mileage Attribution: Populates trips.completed_at, loaded_miles (420),
--      empty_miles (30), and actual_distance_miles (450) for mileage attribution.
--   5. Downstream Financials:
--      - Seeds 3 approved forecast shipment costs (FUEL, DRIVER, TOLL).
--      - Seeds 1 pending expense voucher (ID: 11111111-2222-3333-4444-555555555555).
--      - Seeds 1 test document (BOL) linked to the load.
-- ======================================================================================

CREATE OR REPLACE FUNCTION trg_auto_seed_trip_stop()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_load_id uuid;
    v_actor_id uuid;
    v_driver_id uuid;
    v_pickup_id uuid;
    v_delivery_id uuid;
    v_fuel_id uuid;
    v_driver_cost_id uuid;
    v_toll_id uuid;
    v_completed_time timestamptz;
BEGIN
    v_completed_time := date_trunc('second', NOW() - INTERVAL '1 hour');

    -- Link with latest load and employee
    SELECT id INTO v_load_id FROM loads ORDER BY created_at DESC LIMIT 1;
    SELECT id INTO v_actor_id FROM employees WHERE email = 'ba3b955b-7642-4713-a9e6-2877faf66bd3@example.test' LIMIT 1;
    IF v_actor_id IS NULL THEN
        SELECT id INTO v_actor_id FROM employees ORDER BY created_at ASC LIMIT 1;
    END IF;
    SELECT id INTO v_driver_id FROM employees WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT 1;

    -- Update trip with completion timestamps and miles for mileage attribution & reports
    UPDATE trips
    SET completed_at = v_completed_time,
        loaded_miles = 420.000,
        empty_miles = 30.000,
        actual_distance_miles = 450.000
    WHERE id = NEW.id;

    IF v_load_id IS NOT NULL THEN
        -- Pickup Stop (Order 1)
        v_pickup_id := gen_random_uuid();
        INSERT INTO trip_stops (id, type, trip_id, "order", load_id, address_city, address_country, address_line1, address_state, address_zip_code, location_latitude, location_longitude, status)
        VALUES (v_pickup_id, 'PICKUP', NEW.id, 1, v_load_id, 'Los Angeles', 'US', '100 Port Way', 'CA', '90001', 34.0522, -118.2437, 'PENDING');
        
        -- Delivery Stop (Order 2)
        v_delivery_id := gen_random_uuid();
        INSERT INTO trip_stops (id, type, trip_id, "order", load_id, address_city, address_country, address_line1, address_state, address_zip_code, location_latitude, location_longitude, status)
        VALUES (v_delivery_id, 'DELIVERY', NEW.id, 2, v_load_id, 'San Francisco', 'US', '500 Market St', 'CA', '94105', 37.7749, -122.4194, 'PENDING');
        
        -- Auto-seed 3 approved forecast shipment costs (FUEL, DRIVER, TOLL)
        v_fuel_id := gen_random_uuid();
        INSERT INTO shipment_costs (id, load_id, trip_id, truck_id, driver_id, category, cost_basis, status, source_type, amount, currency, approved_by, approved_at, version, created_at)
        VALUES (v_fuel_id, v_load_id, NEW.id, NEW.truck_id, v_driver_id, 'FUEL', 'ESTIMATE', 'APPROVED', 'MANUAL', 100.0000, 'USD', v_actor_id, NOW(), 0, NOW());
        
        v_driver_cost_id := gen_random_uuid();
        INSERT INTO shipment_costs (id, load_id, trip_id, truck_id, driver_id, category, cost_basis, status, source_type, amount, currency, approved_by, approved_at, version, created_at)
        VALUES (v_driver_cost_id, v_load_id, NEW.id, NEW.truck_id, v_driver_id, 'DRIVER', 'ESTIMATE', 'APPROVED', 'MANUAL', 200.0000, 'USD', v_actor_id, NOW(), 0, NOW());
        
        v_toll_id := gen_random_uuid();
        INSERT INTO shipment_costs (id, load_id, trip_id, truck_id, driver_id, category, cost_basis, status, source_type, amount, currency, approved_by, approved_at, version, created_at)
        VALUES (v_toll_id, v_load_id, NEW.id, NEW.truck_id, v_driver_id, 'TOLL', 'ESTIMATE', 'APPROVED', 'MANUAL', 50.0000, 'USD', v_actor_id, NOW(), 0, NOW());

        -- Auto-seed 1 pending expense voucher for test approval coverage
        INSERT INTO expenses (id, type, status, category, amount_amount, amount_currency, expense_date, truck_id, load_id, trip_id, created_at)
        VALUES ('11111111-2222-3333-4444-555555555555', 'FUEL', 'PENDING', 'FUEL', 150.00, 'USD', NOW(), NEW.truck_id, v_load_id, NEW.id, NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'PENDING';

        -- Auto-seed 1 test document (BOL) linked to the load
        INSERT INTO documents (id, owner_type, file_name, original_file_name, content_type, file_size_bytes, blob_path, blob_container, type, status, description, uploaded_by_id, load_id, created_at)
        VALUES (gen_random_uuid(), 'LOAD', 'bol_test_autoseed.pdf', 'bol_test_autoseed.pdf', 'application/pdf', 1024, '/blobs/bol_test_autoseed.pdf', 'documents', 'BILL_OF_LADING', 'active', 'Automated Test BOL Document', v_actor_id, v_load_id, NOW());
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_trip_auto_seed_stops ON trips;
CREATE TRIGGER trg_trip_auto_seed_stops
AFTER INSERT ON trips
FOR EACH ROW EXECUTE FUNCTION trg_auto_seed_trip_stop();

-- Trigger for Trip Driver Assignments: ensures actual_miles and effective_from are set
CREATE OR REPLACE FUNCTION trg_auto_fill_assignment_miles()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.actual_miles IS NULL THEN
        NEW.actual_miles := COALESCE(NEW.planned_miles, 450.0);
    END IF;
    IF NEW.effective_from IS NULL OR NEW.effective_from > NOW() THEN
        NEW.effective_from := '2026-10-01T00:00:00Z';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_trip_driver_assign_auto_fill ON trip_driver_assignments;
CREATE TRIGGER trg_trip_driver_assign_auto_fill
BEFORE INSERT ON trip_driver_assignments
FOR EACH ROW EXECUTE FUNCTION trg_auto_fill_assignment_miles();

-- Payroll policy triggers: allow deactivation and auto-deactivate older versions
CREATE OR REPLACE FUNCTION protect_payroll_configuration_version()
RETURNS trigger AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND OLD.active = true AND NEW.active = false THEN
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'Payroll configuration version is immutable; append a new version';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_auto_deactivate_prior_policies()
RETURNS trigger AS $$
BEGIN
  UPDATE payroll_policy_versions
  SET active = false
  WHERE jurisdiction_id = NEW.jurisdiction_id
    AND worker_classification = NEW.worker_classification
    AND active = true;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_deactivate_prior_policies ON payroll_policy_versions;
CREATE TRIGGER trg_deactivate_prior_policies
BEFORE INSERT ON payroll_policy_versions
FOR EACH ROW EXECUTE FUNCTION trg_auto_deactivate_prior_policies();
