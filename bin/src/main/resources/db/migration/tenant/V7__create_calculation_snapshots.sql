-- V7__create_calculation_snapshots.sql
CREATE TABLE calculation_snapshots (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID NOT NULL,
    calculation_type VARCHAR(60) NOT NULL,
    engine_name VARCHAR(100) NOT NULL,
    engine_version VARCHAR(50) NOT NULL,
    policy_type VARCHAR(80),
    policy_id UUID,
    policy_version VARCHAR(50),
    input_json JSONB NOT NULL,
    result_json JSONB NOT NULL,
    currency VARCHAR(3),
    checksum VARCHAR(128),
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_by UUID,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_calculation_snapshots_entity ON calculation_snapshots(entity_type, entity_id);
CREATE INDEX ix_calculation_snapshots_correlation ON calculation_snapshots(correlation_id);
