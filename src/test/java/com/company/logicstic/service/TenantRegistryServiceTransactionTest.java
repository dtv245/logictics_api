package com.company.logicstic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.company.logicstic.config.TenancyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class TenantRegistryServiceTransactionTest {

    @Test
    void registryReadsUseControlTransactionManager() {
        Transactional transactional = TenantRegistryService.class.getAnnotation(Transactional.class);

        assertThat(transactional.transactionManager()).isEqualTo("controlTransactionManager");
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void registryWritesUseControlTransactionManager() throws NoSuchMethodException {
        Transactional transactional = TenantRegistryService.class
                .getMethod("registerActiveTenant", String.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.transactionManager()).isEqualTo("controlTransactionManager");
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void legacyRegistryInitializationRemainsAvailableWhenEnabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenancyProperties properties = new TenancyProperties();
        properties.getRegistry().setAutoInitialize(true);
        TenantRegistryService service = new TenantRegistryService(jdbcTemplate, properties, mock(TenantCredentialCipher.class));

        service.initializeRegistrySchema();

        verify(jdbcTemplate).execute(org.mockito.ArgumentMatchers.contains("CREATE TABLE IF NOT EXISTS tenant_registry"));
    }

    @Test
    void legacyRegistryInitializationSkipsDdlWhenFlywayOwnsTheSchema() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenancyProperties properties = new TenancyProperties();
        properties.getRegistry().setAutoInitialize(false);
        TenantRegistryService service = new TenantRegistryService(jdbcTemplate, properties, mock(TenantCredentialCipher.class));

        service.initializeRegistrySchema();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void legacyInitializerRejectsConcurrentControlFlywayOwnership() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenancyProperties properties = new TenancyProperties();
        properties.getControlMigration().setExecutionEnabled(true);
        properties.getRegistry().setAutoInitialize(true);
        TenantRegistryService service = new TenantRegistryService(jdbcTemplate, properties, mock(TenantCredentialCipher.class));

        assertThatThrownBy(service::initializeRegistrySchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Disable app.tenancy.registry.auto-initialize");
        verifyNoInteractions(jdbcTemplate);
    }
}
