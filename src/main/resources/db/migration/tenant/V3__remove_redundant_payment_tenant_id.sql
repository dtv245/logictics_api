-- Each tenant already has an isolated database selected by TenantRoutingDataSource.
-- The legacy UUID tenant_id has no authoritative UUID source: the registry/JWT tenant key is a
-- string. Keeping it NOT NULL makes every application-created payment fail at insert time.
ALTER TABLE payments DROP COLUMN tenant_id;
