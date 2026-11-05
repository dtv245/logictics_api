package com.company.logicstic.config;

import com.company.logicstic.service.DriverLicensePiiBackfill;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runs the idempotent PII migration after Boot has applied single-database Flyway migrations. */
@Configuration
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DriverLicensePiiBootstrapConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner backfillDriverLicensePii(DriverLicensePiiBackfill backfill, DataSource dataSource) {
        return args -> backfill.backfill(dataSource);
    }
}
