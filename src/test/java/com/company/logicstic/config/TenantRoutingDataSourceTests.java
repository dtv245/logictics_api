package com.company.logicstic.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantRoutingDataSourceTests {

    private TenantRoutingDataSource routingDataSource;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (routingDataSource != null) {
            routingDataSource.close();
        }
    }

    @Test
    void tenantAQueriesDoNotReturnTenantBData() {
        HikariDataSource tenantADataSource = h2DataSource("tenant_a");
        HikariDataSource tenantBDataSource = h2DataSource("tenant_b");
        seedCustomer(tenantADataSource, "tenant A customer");
        seedCustomer(tenantBDataSource, "tenant B customer");

        routingDataSource = new TenantRoutingDataSource();
        routingDataSource.addTenantDataSource("tenant-a", tenantADataSource);
        routingDataSource.addTenantDataSource("tenant-b", tenantBDataSource);
        JdbcTemplate tenantJdbcTemplate = new JdbcTemplate(routingDataSource);

        TenantContext.setTenantId("tenant-a");
        List<String> tenantAResults = tenantJdbcTemplate.queryForList("SELECT name FROM customers", String.class);

        TenantContext.setTenantId("tenant-b");
        List<String> tenantBResults = tenantJdbcTemplate.queryForList("SELECT name FROM customers", String.class);

        assertThat(tenantAResults).containsExactly("tenant A customer");
        assertThat(tenantBResults).containsExactly("tenant B customer");
    }

    private void seedCustomer(HikariDataSource dataSource, String customerName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE customers (id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        jdbcTemplate.update("INSERT INTO customers (id, name) VALUES (RANDOM_UUID(), ?)", customerName);
    }

    private HikariDataSource h2DataSource(String databaseName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }
}
