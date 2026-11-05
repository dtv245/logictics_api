-- Tenant-isolated AI Gap Detection and History tables
CREATE TABLE ai_load_gaps (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    load_id UUID NOT NULL REFERENCES loads(id) ON DELETE CASCADE,
    rule_code VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('BLOCKING', 'WARNING')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED', 'NEEDS_HUMAN')),
    question_template TEXT NOT NULL,
    question_natural TEXT,
    proposed_value TEXT,
    ask_count INT NOT NULL DEFAULT 0,
    max_asks INT NOT NULL DEFAULT 3,
    last_asked_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ,
    dismissed_by UUID,
    dismiss_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_load_gaps_tenant_load_rule UNIQUE (tenant_id, load_id, rule_code)
);

CREATE INDEX idx_ai_load_gaps_load_status ON ai_load_gaps(load_id, status);
CREATE INDEX idx_ai_load_gaps_tenant ON ai_load_gaps(tenant_id);

CREATE TABLE ai_load_gap_history (
    id UUID PRIMARY KEY,
    gap_id UUID NOT NULL REFERENCES ai_load_gaps(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(20) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_load_gap_history_gap ON ai_load_gap_history(gap_id);

ALTER TABLE ai_dispatch_sessions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100);
ALTER TABLE ai_dispatch_decisions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100);

CREATE TABLE ai_runs (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    run_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    load_id UUID,
    model_name VARCHAR(100),
    prompt_version VARCHAR(50),
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(10, 6) NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    result_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_runs_tenant_created ON ai_runs(tenant_id, created_at DESC);
CREATE INDEX idx_ai_runs_load ON ai_runs(load_id);

