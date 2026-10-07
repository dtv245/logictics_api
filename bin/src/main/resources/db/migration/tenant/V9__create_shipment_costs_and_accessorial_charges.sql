-- Phase 3 canonical cost ledger and accessorial charges.
-- V8 is already reserved for the forward-only trip-assignment history fix.

CREATE TABLE shipment_costs (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL,
    trip_id UUID,
    truck_id UUID,
    driver_id UUID,
    category VARCHAR(40) NOT NULL,
    cost_basis VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source_type VARCHAR(40) NOT NULL,
    source_id UUID,
    allocation_method VARCHAR(40),
    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    unit_rate NUMERIC(19,6),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    incurred_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    verified_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    posted_at TIMESTAMPTZ,
    calculation_snapshot_id UUID,
    note VARCHAR(2000),
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),
    CONSTRAINT chk_shipment_costs_basis CHECK (cost_basis IN ('ESTIMATE', 'ACCRUAL', 'ACTUAL')),
    CONSTRAINT chk_shipment_costs_status CHECK (status IN ('DRAFT', 'VERIFIED', 'APPROVED', 'POSTED', 'VOIDED')),
    CONSTRAINT chk_shipment_costs_amount CHECK (amount >= 0),
    CONSTRAINT fk_shipment_costs_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shipment_costs_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    CONSTRAINT fk_shipment_costs_truck FOREIGN KEY (truck_id) REFERENCES trucks(id) ON DELETE SET NULL,
    CONSTRAINT fk_shipment_costs_driver FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE SET NULL,
    CONSTRAINT fk_shipment_costs_snapshot FOREIGN KEY (calculation_snapshot_id) REFERENCES calculation_snapshots(id) ON DELETE SET NULL
);

CREATE INDEX ix_shipment_costs_load_basis_status ON shipment_costs(load_id, cost_basis, status);
CREATE INDEX ix_shipment_costs_source ON shipment_costs(source_type, source_id);
CREATE UNIQUE INDEX uq_shipment_costs_source ON shipment_costs(source_type, source_id)
    WHERE source_id IS NOT NULL;

CREATE TABLE accessorial_charges (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL,
    trip_id UUID,
    trip_stop_id UUID,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    rate NUMERIC(19,6),
    free_quantity NUMERIC(19,6),
    customer_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    company_cost_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    driver_pay_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    occurred_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    document_id UUID,
    calculation_snapshot_id UUID,
    note VARCHAR(2000),
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accessorial_charges_amounts CHECK (
        customer_amount >= 0 AND company_cost_amount >= 0 AND driver_pay_amount >= 0
    ),
    CONSTRAINT fk_accessorial_charges_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE RESTRICT,
    CONSTRAINT fk_accessorial_charges_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL,
    CONSTRAINT fk_accessorial_charges_stop FOREIGN KEY (trip_stop_id) REFERENCES trip_stops(id) ON DELETE SET NULL,
    CONSTRAINT fk_accessorial_charges_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL,
    CONSTRAINT fk_accessorial_charges_snapshot FOREIGN KEY (calculation_snapshot_id) REFERENCES calculation_snapshots(id) ON DELETE SET NULL
);

CREATE INDEX ix_accessorial_charges_load_status ON accessorial_charges(load_id, status);
