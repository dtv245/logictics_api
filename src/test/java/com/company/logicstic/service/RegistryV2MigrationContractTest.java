package com.company.logicstic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegistryV2MigrationContractTest {

    private static final String V1_PATH = "/db/migration/registry/V1__create_tenant_registry.sql";
    private static final String V2_PATH = "/db/migration/registry/V2__create_control_identity_membership_schema.sql";
    private static String sql;
    private static String v1Sql;

    @BeforeAll
    static void readMigration() throws IOException {
        try (var input = RegistryV2MigrationContractTest.class.getResourceAsStream(V2_PATH)) {
            assertThat(input).as("Registry V2 migration resource").isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var input = RegistryV2MigrationContractTest.class.getResourceAsStream(V1_PATH)) {
            assertThat(input).as("Registry V1 migration resource").isNotNull();
            v1Sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void registryV2FollowsV1AndLivesInTheControlMigrationLocation() {
        assertThat(RegistryV2MigrationContractTest.class.getResource(V1_PATH)).isNotNull();
        assertThat(RegistryV2MigrationContractTest.class.getResource(V2_PATH)).isNotNull();
        assertThat(version(V1_PATH)).isLessThan(version(V2_PATH));
        assertThat(ControlFlywayMigrationService.REGISTRY_LOCATION)
                .isEqualTo("classpath:db/migration/registry");
        assertThat(RegistryV2MigrationContractTest.class.getResource(
                "/db/migration/tenant/V2__create_control_identity_membership_schema.sql")).isNull();
    }

    @Test
    void registryV1RemainsUnchanged() {
        String expectedV1 = """
                CREATE TABLE IF NOT EXISTS tenant_registry (
                    tenant_id VARCHAR(50) PRIMARY KEY,
                    db_url VARCHAR(255) NOT NULL,
                    db_username VARCHAR(100) NOT NULL,
                    db_password VARCHAR(255) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                );
                """;

        assertThat(v1Sql).isEqualTo(expectedV1);
    }

    @Test
    void createsOnlyTheSixControlPlaneTables() {
        assertThat(sql).contains(
                "CREATE TABLE organization_units",
                "CREATE TABLE platform_users",
                "CREATE TABLE identity_providers",
                "CREATE TABLE external_identities",
                "CREATE TABLE tenant_memberships",
                "CREATE TABLE control_audit_events");
        assertThat(sql).doesNotContain("CREATE TABLE IF NOT EXISTS", "INSERT INTO");
    }

    @Test
    void usesExpectedIdentityTypesAndTenantReferences() {
        assertThat(sql).contains(
                "tenant_id VARCHAR(50)",
                "local_employee_id UUID",
                "FOREIGN KEY (tenant_id) REFERENCES tenant_registry (tenant_id)",
                "FOREIGN KEY (platform_user_id) REFERENCES platform_users (id)",
                "FOREIGN KEY (identity_provider_id) REFERENCES identity_providers (id)",
                "UNIQUE (platform_user_id, tenant_id)",
                "UNIQUE (identity_provider_id, subject)");
        assertThat(sql).doesNotContain("REFERENCES employees", "REFERENCES tenant_roles");
    }

    @Test
    void companyTenantMappingAndOrganizationHierarchyAreConstrained() {
        assertThat(sql).contains(
                "CHECK (unit_type IN ('GROUP', 'COMPANY', 'BRANCH', 'DEPARTMENT'))",
                "CHECK ((unit_type = 'COMPANY' AND tenant_id IS NOT NULL)",
                "CREATE UNIQUE INDEX uq_organization_units_tenant_id",
                "FOREIGN KEY (parent_id) REFERENCES organization_units (id) ON DELETE RESTRICT");
    }

    @Test
    void externalProviderSubjectIsTheUniqueIdentityAndEmailIsNotUnique() {
        assertThat(sql).contains(
                "UNIQUE (provider_type, provider_identity, client_id)",
                "UNIQUE (identity_provider_id, subject)",
                "CREATE INDEX ix_platform_users_email");
        assertThat(sql).doesNotContain("UNIQUE (email)", "uq_platform_users_email");
    }

    @Test
    void auditOrganizationAndTenantForeignKeysHandleNullableContextSafely() {
        String auditTable = normalizeWhitespace(tableDefinition("control_audit_events"));

        assertThat(auditTable).contains(
                "FOREIGN KEY (actor_platform_user_id) REFERENCES platform_users (id) ON DELETE SET NULL",
                "FOREIGN KEY (tenant_id) REFERENCES tenant_registry (tenant_id) ON DELETE RESTRICT",
                "CONSTRAINT fk_control_audit_organization FOREIGN KEY (organization_unit_id) REFERENCES organization_units (id) ON DELETE RESTRICT",
                "CONSTRAINT fk_control_audit_events_organization_tenant FOREIGN KEY (organization_unit_id, tenant_id) REFERENCES organization_units (id, tenant_id) ON DELETE RESTRICT",
                "tenant_id VARCHAR(50)",
                "organization_unit_id UUID",
                "ON DELETE RESTRICT");
        assertThat(auditTable).doesNotContain("ON DELETE CASCADE");
    }

    @Test
    void auditAclRejectsEffectiveWritesPublicGrantsSetRoleAndWrongOwnership() {
        String auditAclGuard = sql.substring(sql.lastIndexOf("DO $$"));
        String rolePreflight = sql.substring(sql.indexOf("DO $$"), sql.indexOf("CREATE TABLE organization_units"));

        assertThat(sql).contains(
                "REVOKE ALL PRIVILEGES ON TABLE control_audit_events FROM control_app",
                "GRANT SELECT, INSERT ON TABLE control_audit_events TO control_app",
                "GRANT SELECT ON TABLE control_audit_events TO control_readonly",
                "has_table_privilege('control_app', 'public.control_audit_events', 'UPDATE')",
                "has_any_column_privilege('control_app', 'public.control_audit_events', 'UPDATE')",
                "has_table_privilege('control_app', 'public.control_audit_events', 'DELETE')",
                "has_table_privilege('control_app', 'public.control_audit_events', 'TRUNCATE')",
                "has_table_privilege('public', 'public.control_audit_events', 'UPDATE')",
                "has_any_column_privilege('public', 'public.control_audit_events', 'UPDATE')",
                "has_table_privilege('public', 'public.control_audit_events', 'DELETE')",
                "pg_has_role('control_app', settable_role.rolname, 'SET')",
                "pg_has_role('control_readonly', settable_role.rolname, 'SET')",
                "control_audit_events must be owned by control_migrator");
        assertThat(rolePreflight).contains(
                "current_user <> 'control_migrator'",
                "pg_has_role('control_app', 'control_migrator', 'MEMBER')",
                "pg_has_role('control_readonly', 'control_migrator', 'MEMBER')",
                "WHERE elevated_role.rolsuper");
        assertThat(sql).doesNotContain(
                "GRANT UPDATE ON TABLE control_audit_events TO control_app",
                "GRANT DELETE ON TABLE control_audit_events TO control_app",
                "GRANT TRUNCATE ON TABLE control_audit_events TO control_app",
                "GRANT UPDATE, DELETE ON ALL TABLES");
    }

    @Test
    void doesNotDefineCredentialOrTokenStorage() {
        assertThat(sql.toLowerCase()).doesNotContain(
                "client_secret varchar", "password varchar", "access_token varchar",
                "refresh_token varchar", "private_key varchar", "jwt varchar");
    }

    @Test
    void definesStatusConstraintsAndTimezoneAwareAuditTime() {
        assertThat(sql).contains(
                "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'",
                "CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE'))",
                "occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()");
    }

    private static int version(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        Matcher matcher = Pattern.compile("^V(\\d+)__.*\\.sql$").matcher(fileName);
        assertThat(matcher.matches()).as("versioned migration filename: %s", fileName).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private static String tableDefinition(String tableName) {
        String marker = "CREATE TABLE " + tableName + " (";
        int start = sql.indexOf(marker);
        assertThat(start).as("table definition for %s", tableName).isGreaterThanOrEqualTo(0);

        int open = sql.indexOf('(', start);
        int depth = 0;
        for (int index = open; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return sql.substring(start, index + 1);
            }
        }
        throw new AssertionError("Unclosed table definition for " + tableName);
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
