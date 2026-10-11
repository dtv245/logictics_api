package com.company.logicstic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabaseUrlUtilsTest {

    @Test
    @DisplayName("Returns existing jdbc:postgresql URL without changes")
    void normalize_standardJdbcPostgresUrl() {
        var result = DatabaseUrlUtils.normalize(
                "jdbc:postgresql://localhost:5432/logistics_db", "user1", "pass1");

        assertThat(result.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/logistics_db");
        assertThat(result.username()).isEqualTo("user1");
        assertThat(result.password()).isEqualTo("pass1");
    }

    @Test
    @DisplayName("Normalizes postgresql:// URL with embedded credentials from Render")
    void normalize_renderPostgresUrlWithCredentials() {
        var result = DatabaseUrlUtils.normalize(
                "postgresql://render_user:render_pass@dpg-abc-a.oregon-postgres.render.com:5432/logistics_db?sslmode=require",
                null,
                null);

        assertThat(result.jdbcUrl()).isEqualTo("jdbc:postgresql://dpg-abc-a.oregon-postgres.render.com:5432/logistics_db?sslmode=require");
        assertThat(result.username()).isEqualTo("render_user");
        assertThat(result.password()).isEqualTo("render_pass");
    }

    @Test
    @DisplayName("Normalizes postgres:// scheme without port or user info")
    void normalize_postgresSchemeWithoutUserInfo() {
        var result = DatabaseUrlUtils.normalize(
                "postgres://localhost/tenant_test", "default_user", "default_pass");

        assertThat(result.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost/tenant_test");
        assertThat(result.username()).isEqualTo("default_user");
        assertThat(result.password()).isEqualTo("default_pass");
    }

    @Test
    @DisplayName("Preserves explicit username/password over embedded URI credentials")
    void normalize_explicitCredentialsTakePrecedence() {
        var result = DatabaseUrlUtils.normalize(
                "postgresql://uri_user:uri_pass@host:5432/db", "explicit_user", "explicit_pass");

        assertThat(result.jdbcUrl()).isEqualTo("jdbc:postgresql://host:5432/db");
        assertThat(result.username()).isEqualTo("explicit_user");
        assertThat(result.password()).isEqualTo("explicit_pass");
    }

    @Test
    @DisplayName("Returns blank or null URLs as is")
    void normalize_nullOrEmpty() {
        assertThat(DatabaseUrlUtils.normalize(null, "u", "p").jdbcUrl()).isNull();
        assertThat(DatabaseUrlUtils.normalize("  ", "u", "p").jdbcUrl()).isEqualTo("  ");
    }

    @Test
    @DisplayName("TenantDataSourceConfiguration throws clear IllegalStateException when registry url is blank")
    void tenantRegistryDataSource_missingUrl_throwsIllegalStateException() {
        TenantDataSourceConfiguration config = new TenantDataSourceConfiguration();
        TenancyProperties props = new TenancyProperties();
        props.setEnabled(true);
        props.getRegistry().setUrl(""); // empty url

        assertThatThrownBy(() -> config.tenantRegistryDataSource(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_REGISTRY_DB_URL")
                .hasMessageContaining("app.tenancy.registry.url");
    }
}
