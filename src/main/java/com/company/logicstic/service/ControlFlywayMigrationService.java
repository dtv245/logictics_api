package com.company.logicstic.service;

import javax.sql.DataSource;

import com.company.logicstic.config.TenancyProperties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Explicit, on-demand Control schema migration. This service has no startup
 * runner: an operator-owned invocation is required, and the feature is absent
 * unless app.tenancy.control-migration.execution-enabled=true.
 */
@Service
@ConditionalOnProperty(prefix = "app.tenancy.control-migration", name = "execution-enabled", havingValue = "true")
public class ControlFlywayMigrationService {

    static final String REGISTRY_LOCATION = "classpath:db/migration/registry";

    private final DataSource controlDataSource;
    private final TenancyProperties properties;

    public ControlFlywayMigrationService(
            @Qualifier("controlDataSource") DataSource controlDataSource,
            TenancyProperties properties) {
        this.controlDataSource = controlDataSource;
        this.properties = properties;
    }

    public MigrateResult migrateControlSchema() {
        if (!properties.getControlMigration().isExecutionEnabled()) {
            throw new IllegalStateException("Control Database migrations are disabled");
        }
        if (properties.getRegistry().isAutoInitialize()) {
            throw new IllegalStateException(
                    "Disable app.tenancy.registry.auto-initialize before running Control Flyway migrations");
        }
        return createFlyway().migrate();
    }

    Flyway createFlyway() {
        return Flyway.configure()
                .dataSource(controlDataSource)
                .locations(REGISTRY_LOCATION)
                .schemas("public")
                .defaultSchema("public")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
    }
}
