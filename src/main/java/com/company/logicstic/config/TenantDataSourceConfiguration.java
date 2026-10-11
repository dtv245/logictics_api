package com.company.logicstic.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

import org.springframework.util.StringUtils;

@Configuration
@EnableAsync
@EnableConfigurationProperties(TenancyProperties.class)
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantDataSourceConfiguration {

    @Bean
    @Primary
    public TenantRoutingDataSource dataSource() {
        return new TenantRoutingDataSource();
    }

    @Bean
    public DataSource tenantRegistryDataSource(TenancyProperties properties) {
        TenancyProperties.Registry registry = properties.getRegistry();
        String rawUrl = registry.getUrl();
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalStateException(
                "Missing required configuration: 'app.tenancy.registry.url' (environment variable: TENANT_REGISTRY_DB_URL). "
                + "When TENANCY_ENABLED=true, a valid PostgreSQL JDBC URL is required for the tenant registry database.");
        }

        DatabaseUrlUtils.NormalizedJdbcUrl normalized = DatabaseUrlUtils.normalize(
                rawUrl, registry.getUsername(), registry.getPassword());

        HikariConfig config = new HikariConfig();
        config.setPoolName("tenant-registry");
        config.setDriverClassName(registry.getDriverClassName());
        config.setJdbcUrl(normalized.jdbcUrl());
        config.setUsername(normalized.username());
        config.setPassword(normalized.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate tenantRegistryJdbcTemplate(
            @Qualifier("tenantRegistryDataSource") DataSource tenantRegistryDataSource) {
        return new JdbcTemplate(tenantRegistryDataSource);
    }

}
