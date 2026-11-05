-- Control Plane identity and membership foundation.
-- Run with the dedicated control_migrator role. This migration intentionally
-- fails if the runtime roles needed for the audit ACL policy are not provisioned.
DO $$
BEGIN
    IF to_regrole('control_migrator') IS NULL THEN
        RAISE EXCEPTION 'Required role control_migrator is missing';
    END IF;
    IF to_regrole('control_app') IS NULL THEN
        RAISE EXCEPTION 'Required role control_app is missing';
    END IF;
    IF to_regrole('control_readonly') IS NULL THEN
        RAISE EXCEPTION 'Required role control_readonly is missing';
    END IF;
    IF current_user <> 'control_migrator' THEN
        RAISE EXCEPTION 'Control schema migrations must run as control_migrator';
    END IF;
    IF pg_has_role('control_app', 'control_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'control_app must not be a member of control_migrator';
    END IF;
    IF pg_has_role('control_readonly', 'control_migrator', 'MEMBER') THEN
        RAISE EXCEPTION 'control_readonly must not be a member of control_migrator';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM pg_roles elevated_role
        WHERE elevated_role.rolsuper
          AND (
              pg_has_role('control_app', elevated_role.rolname, 'MEMBER')
              OR pg_has_role('control_readonly', elevated_role.rolname, 'MEMBER')
          )
    ) THEN
        RAISE EXCEPTION 'Runtime Control roles must not be members of a superuser role';
    END IF;
END;
$$;

CREATE TABLE organization_units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    unit_type VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organization_units_code UNIQUE (code),
    CONSTRAINT uq_organization_units_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_organization_units_parent
        FOREIGN KEY (parent_id) REFERENCES organization_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_organization_units_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant_registry (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_organization_units_type
        CHECK (unit_type IN ('GROUP', 'COMPANY', 'BRANCH', 'DEPARTMENT')),
    CONSTRAINT ck_organization_units_company_tenant
        CHECK ((unit_type = 'COMPANY' AND tenant_id IS NOT NULL)
            OR (unit_type <> 'COMPANY' AND tenant_id IS NULL)),
    CONSTRAINT ck_organization_units_not_own_parent
        CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_organization_units_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_organization_units_tenant_id
    ON organization_units (tenant_id)
    WHERE tenant_id IS NOT NULL;
CREATE INDEX ix_organization_units_parent_id
    ON organization_units (parent_id)
    WHERE parent_id IS NOT NULL;
CREATE INDEX ix_organization_units_status_type
    ON organization_units (status, unit_type);

CREATE TABLE platform_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(200),
    email VARCHAR(320),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_platform_users_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);
-- Email is profile metadata, not a unique or authoritative identity key.
CREATE INDEX ix_platform_users_email ON platform_users (email) WHERE email IS NOT NULL;
CREATE INDEX ix_platform_users_status ON platform_users (status);

CREATE TABLE identity_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_type VARCHAR(20) NOT NULL,
    provider_key VARCHAR(100) NOT NULL,
    -- OIDC: exact configured issuer. LARK: configured provider/tenant namespace.
    provider_identity VARCHAR(500) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    audience VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_identity_providers_provider_key UNIQUE (provider_key),
    CONSTRAINT uq_identity_providers_external_scope
        UNIQUE (provider_type, provider_identity, client_id),
    CONSTRAINT ck_identity_providers_type
        CHECK (provider_type IN ('OIDC', 'LARK')),
    CONSTRAINT ck_identity_providers_identity_nonblank
        CHECK (length(btrim(provider_identity)) > 0),
    CONSTRAINT ck_identity_providers_client_nonblank
        CHECK (length(btrim(client_id)) > 0),
    CONSTRAINT ck_identity_providers_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);
CREATE INDEX ix_identity_providers_status_type
    ON identity_providers (status, provider_type);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_user_id UUID NOT NULL,
    identity_provider_id UUID NOT NULL,
    subject VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_external_identities_platform_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_external_identities_provider
        FOREIGN KEY (identity_provider_id) REFERENCES identity_providers (id) ON DELETE RESTRICT,
    CONSTRAINT uq_external_identities_provider_subject
        UNIQUE (identity_provider_id, subject),
    CONSTRAINT ck_external_identities_subject_nonblank
        CHECK (length(btrim(subject)) > 0)
);
CREATE INDEX ix_external_identities_platform_user
    ON external_identities (platform_user_id);

CREATE TABLE tenant_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_user_id UUID NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    -- Logical reference to employees.id in that tenant database; intentionally no FK.
    local_employee_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tenant_memberships_platform_user
        FOREIGN KEY (platform_user_id) REFERENCES platform_users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tenant_memberships_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant_registry (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT uq_tenant_memberships_user_tenant
        UNIQUE (platform_user_id, tenant_id),
    CONSTRAINT ck_tenant_memberships_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))
);
CREATE INDEX ix_tenant_memberships_user_status
    ON tenant_memberships (platform_user_id, status);
CREATE INDEX ix_tenant_memberships_tenant_status
    ON tenant_memberships (tenant_id, status);

CREATE TABLE control_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actor_platform_user_id UUID,
    tenant_id VARCHAR(50),
    organization_unit_id UUID,
    event_type VARCHAR(100) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_control_audit_events_actor
        FOREIGN KEY (actor_platform_user_id) REFERENCES platform_users (id) ON DELETE SET NULL,
    CONSTRAINT fk_control_audit_events_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant_registry (tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_control_audit_organization
        FOREIGN KEY (organization_unit_id) REFERENCES organization_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_control_audit_events_organization_tenant
        FOREIGN KEY (organization_unit_id, tenant_id)
        REFERENCES organization_units (id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_control_audit_events_type_nonblank
        CHECK (length(btrim(event_type)) > 0),
    CONSTRAINT ck_control_audit_events_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object')
);
COMMENT ON COLUMN control_audit_events.metadata IS
    'Allowlisted non-sensitive context only; never store JWTs, refresh tokens, credentials, keys, or raw sensitive payloads.';
CREATE INDEX ix_control_audit_events_occurred_at
    ON control_audit_events (occurred_at DESC);
CREATE INDEX ix_control_audit_events_actor_time
    ON control_audit_events (actor_platform_user_id, occurred_at DESC)
    WHERE actor_platform_user_id IS NOT NULL;
CREATE INDEX ix_control_audit_events_tenant_time
    ON control_audit_events (tenant_id, occurred_at DESC)
    WHERE tenant_id IS NOT NULL;
CREATE INDEX ix_control_audit_events_organization_time
    ON control_audit_events (organization_unit_id, occurred_at DESC)
    WHERE organization_unit_id IS NOT NULL;
CREATE INDEX ix_control_audit_events_type_time
    ON control_audit_events (event_type, occurred_at DESC);

-- Existing default privileges grant broad DML to control_app. Override them for
-- audit records: app can read/append, readonly can only read, neither can edit history.
REVOKE ALL PRIVILEGES ON TABLE control_audit_events FROM control_app;
GRANT SELECT, INSERT ON TABLE control_audit_events TO control_app;
REVOKE ALL PRIVILEGES ON TABLE control_audit_events FROM control_readonly;
GRANT SELECT ON TABLE control_audit_events TO control_readonly;

DO $$
BEGIN
    IF (
        SELECT pg_get_userbyid(audit_table.relowner)
        FROM pg_class audit_table
        JOIN pg_namespace audit_schema ON audit_schema.oid = audit_table.relnamespace
        WHERE audit_schema.nspname = 'public'
          AND audit_table.relname = 'control_audit_events'
    ) IS DISTINCT FROM 'control_migrator' THEN
        RAISE EXCEPTION 'control_audit_events must be owned by control_migrator';
    END IF;

    IF has_table_privilege('control_app', 'public.control_audit_events', 'UPDATE')
        OR has_any_column_privilege('control_app', 'public.control_audit_events', 'UPDATE')
        OR has_table_privilege('control_app', 'public.control_audit_events', 'DELETE')
        OR has_table_privilege('control_app', 'public.control_audit_events', 'TRUNCATE')
        OR has_table_privilege('public', 'public.control_audit_events', 'UPDATE')
        OR has_any_column_privilege('public', 'public.control_audit_events', 'UPDATE')
        OR has_table_privilege('public', 'public.control_audit_events', 'DELETE')
        OR has_table_privilege('public', 'public.control_audit_events', 'TRUNCATE')
        OR EXISTS (
            SELECT 1
            FROM pg_roles settable_role
            WHERE pg_has_role('control_app', settable_role.rolname, 'SET')
              AND (
                  has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'UPDATE')
                  OR has_any_column_privilege(settable_role.rolname, 'public.control_audit_events', 'UPDATE')
                  OR has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'DELETE')
                  OR has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'TRUNCATE')
              )
        ) THEN
        RAISE EXCEPTION 'control_app has effective mutation privileges on control_audit_events';
    END IF;
    IF has_table_privilege('control_readonly', 'public.control_audit_events', 'INSERT')
        OR has_any_column_privilege('control_readonly', 'public.control_audit_events', 'INSERT')
        OR has_table_privilege('control_readonly', 'public.control_audit_events', 'UPDATE')
        OR has_any_column_privilege('control_readonly', 'public.control_audit_events', 'UPDATE')
        OR has_table_privilege('control_readonly', 'public.control_audit_events', 'DELETE')
        OR has_table_privilege('control_readonly', 'public.control_audit_events', 'TRUNCATE')
        OR has_table_privilege('public', 'public.control_audit_events', 'INSERT')
        OR has_any_column_privilege('public', 'public.control_audit_events', 'INSERT')
        OR has_table_privilege('public', 'public.control_audit_events', 'UPDATE')
        OR has_any_column_privilege('public', 'public.control_audit_events', 'UPDATE')
        OR has_table_privilege('public', 'public.control_audit_events', 'DELETE')
        OR has_table_privilege('public', 'public.control_audit_events', 'TRUNCATE')
        OR EXISTS (
            SELECT 1
            FROM pg_roles settable_role
            WHERE pg_has_role('control_readonly', settable_role.rolname, 'SET')
              AND (
                  has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'INSERT')
                  OR has_any_column_privilege(settable_role.rolname, 'public.control_audit_events', 'INSERT')
                  OR has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'UPDATE')
                  OR has_any_column_privilege(settable_role.rolname, 'public.control_audit_events', 'UPDATE')
                  OR has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'DELETE')
                  OR has_table_privilege(settable_role.rolname, 'public.control_audit_events', 'TRUNCATE')
              )
        ) THEN
        RAISE EXCEPTION 'control_readonly has effective mutation privileges on control_audit_events';
    END IF;
END;
$$;
