package com.company.logicstic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import com.company.logicstic.config.TenancyProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class ControlFlywayMigrationServiceTest {

    @Test
    void controlMigrationIsDisabledByDefault() {
        assertThat(new TenancyProperties().getControlMigration().isExecutionEnabled()).isFalse();
        ConditionalOnProperty condition = ControlFlywayMigrationService.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.prefix()).isEqualTo("app.tenancy.control-migration");
        assertThat(condition.name()).containsExactly("execution-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void tenantMigrationLocationRemainsTenantOnly() {
        assertThat(new TenancyProperties().getMigration().getLocations())
                .containsExactly("classpath:db/migration/tenant");
    }

    @Test
    void flywayConfigurationIsRegistryOnlyAndNonDestructive() {
        DataSource controlDataSource = mock(DataSource.class);
        ControlFlywayMigrationService service = new ControlFlywayMigrationService(
                controlDataSource, new TenancyProperties());

        Flyway flyway = service.createFlyway();

        assertThat(flyway.getConfiguration().getDataSource()).isSameAs(controlDataSource);
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Object::toString)
                .containsExactly(ControlFlywayMigrationService.REGISTRY_LOCATION);
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(flyway.getConfiguration().isValidateOnMigrate()).isTrue();
        assertThat(flyway.getConfiguration().getDefaultSchema()).isEqualTo("public");
    }

    @Test
    void migrationRequiresExplicitOptIn() {
        TenancyProperties properties = new TenancyProperties();
        ControlFlywayMigrationService service = new ControlFlywayMigrationService(mock(DataSource.class), properties);

        assertThatThrownBy(service::migrateControlSchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void migrationRefusesToCompeteWithLegacyRegistryInitialization() {
        TenancyProperties properties = new TenancyProperties();
        properties.getControlMigration().setExecutionEnabled(true);
        properties.getRegistry().setAutoInitialize(true);
        ControlFlywayMigrationService service = new ControlFlywayMigrationService(mock(DataSource.class), properties);

        assertThatThrownBy(service::migrateControlSchema)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto-initialize");
    }
}
