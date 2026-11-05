package com.company.logicstic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

class ControlPlaneTransactionConfigurationTest {

    private final ControlPlaneTransactionConfiguration configuration = new ControlPlaneTransactionConfiguration();

    @Test
    void controlJdbcTemplateUsesTheSharedControlRegistryDataSource() {
        DataSource controlDataSource = mock(DataSource.class);

        JdbcTemplate jdbcTemplate = new TenantDataSourceConfiguration()
                .controlJdbcTemplate(controlDataSource);

        assertThat(jdbcTemplate.getDataSource()).isSameAs(controlDataSource);
    }

    @Test
    void unqualifiedBusinessJdbcTemplateUsesTenantRoutingDataSource() throws NoSuchMethodException {
        DataSource tenantRoutingDataSource = mock(DataSource.class);

        JdbcTemplate jdbcTemplate = new TenantDataSourceConfiguration().tenantJdbcTemplate(tenantRoutingDataSource);

        assertThat(jdbcTemplate.getDataSource()).isSameAs(tenantRoutingDataSource);
        assertThat(TenantDataSourceConfiguration.class
                .getMethod("tenantJdbcTemplate", DataSource.class)
                .isAnnotationPresent(Primary.class)).isTrue();
    }

    @Test
    void controlTransactionManagerUsesQualifiedControlDataSource() {
        DataSource controlDataSource = mock(DataSource.class);

        PlatformTransactionManager transactionManager = configuration.controlTransactionManager(controlDataSource);

        assertThat(transactionManager).isInstanceOf(DataSourceTransactionManager.class);
        assertThat(((DataSourceTransactionManager) transactionManager).getDataSource()).isSameAs(controlDataSource);
    }

    @Test
    void tenantJpaTransactionManagerRemainsThePrimaryDefault() throws NoSuchMethodException {
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        PlatformTransactionManager transactionManager = configuration.tenantTransactionManager(entityManagerFactory);
        Bean bean = ControlPlaneTransactionConfiguration.class
                .getMethod("tenantTransactionManager", EntityManagerFactory.class)
                .getAnnotation(Bean.class);

        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(((JpaTransactionManager) transactionManager).getEntityManagerFactory())
                .isSameAs(entityManagerFactory);
        assertThat(ControlPlaneTransactionConfiguration.class
                .getMethod("tenantTransactionManager", EntityManagerFactory.class)
                .isAnnotationPresent(Primary.class)).isTrue();
        assertThat(bean.name()).contains("transactionManager", "tenantTransactionManager");
    }

    @Test
    void registryDataSourceKeepsLegacyNameAndAddsControlAlias() throws NoSuchMethodException {
        Bean bean = TenantDataSourceConfiguration.class
                .getMethod("tenantRegistryDataSource", TenancyProperties.class)
                .getAnnotation(Bean.class);

        assertThat(bean.name()).contains("tenantRegistryDataSource", "controlDataSource");
    }
}
