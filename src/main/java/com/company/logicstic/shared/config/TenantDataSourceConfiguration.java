package com.company.logicstic.shared.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;

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
    HikariConfig config = new HikariConfig();
    config.setPoolName("tenant-registry");
    config.setDriverClassName(registry.getDriverClassName());
    config.setJdbcUrl(registry.getUrl());
    config.setUsername(registry.getUsername());
    config.setPassword(registry.getPassword());
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
