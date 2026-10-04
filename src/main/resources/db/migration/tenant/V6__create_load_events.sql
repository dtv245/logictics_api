-- V6__create_load_events.sql
CREATE TABLE load_events (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL,
    trip_id UUID,
    trip_stop_id UUID,
    event_type VARCHAR(60) NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40),
    occurred_at TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    source VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    actor_id UUID,
    document_id UUID,
    note VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_load_events_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);

CREATE INDEX ix_load_events_load_occurred ON load_events(load_id, occurred_at);
