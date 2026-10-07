package com.company.logicstic.config;

import com.company.logicstic.service.TenantMigrationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Boot never migrates the unbound router; readiness waits for the explicit tenant batch. */
@Configuration
@ConditionalOnProperty(prefix="app.tenancy", name="enabled", havingValue="true")
public class TenantMigrationBootstrapConfiguration {
    @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<TenantJwtClaimFilter> tenantFilterRegistration(TenantJwtClaimFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Ordered inside the security chain after validated JWT authentication.
        return registration;
    }
    @Bean public FlywayMigrationStrategy routedFlywayMigrationStrategy() {
        return routingFlyway -> { /* TenantMigrationService owns each physical tenant schema. */ };
    }
    @Bean public ApplicationRunner migrateActiveTenants(TenantMigrationService migrations) {
        return args -> {
            var report = migrations.migrateAllActiveTenants();
            if (!report.success())
                throw new IllegalStateException("Tenant migration batch stopped; migrated=" + report.migratedTenants()
                        + ", pending=" + report.pendingTenants() + ", failed=" + report.failedTenant());
        };
    }
}
