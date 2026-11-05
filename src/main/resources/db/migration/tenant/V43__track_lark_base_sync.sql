-- Tenant-local sync metadata. No provider credentials. No FK: retain tombstones after deletion.
CREATE TABLE lark_base_sync_links (
 tenant_id VARCHAR(100) NOT NULL,
 app_scope VARCHAR(64) NOT NULL,
 table_id VARCHAR(100) NOT NULL,
 domain VARCHAR(16) NOT NULL CHECK(domain IN ('load','driver')),
 entity_id UUID NOT NULL,
 record_id VARCHAR(100),
 client_token UUID NOT NULL,
 pending_payload TEXT NOT NULL,
 content_hash VARCHAR(64),
 mapping_hash VARCHAR(64) NOT NULL,
 last_synced_at TIMESTAMP WITH TIME ZONE,
 state VARCHAR(24) NOT NULL DEFAULT 'PENDING'
  CHECK(state IN ('PENDING','SYNCED','ERROR','CONFLICT','MISSING','NOT_SYNCED')),
 last_error VARCHAR(100),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(tenant_id,app_scope,table_id,domain,entity_id),
 UNIQUE(tenant_id,app_scope,table_id,record_id)
);
CREATE INDEX idx_lark_base_sync_state ON lark_base_sync_links(tenant_id,state,entity_id);
CREATE TABLE lark_base_sync_audit (
 id UUID PRIMARY KEY,
 tenant_id VARCHAR(100) NOT NULL,
 domain VARCHAR(16) NOT NULL,
 entity_id UUID NOT NULL,
 changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 -- Only mapped business field values; never credentials or complete provider envelopes.
 previous_fields TEXT NOT NULL
);

