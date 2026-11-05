package com.company.logicstic.config;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Keeps tenant JPA transactions as the application default while exposing a
 * separately qualified transaction manager for Control Database JDBC work.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class ControlPlaneTransactionConfiguration {

    @Bean(name = {"tenantTransactionManager", "transactionManager"})
    @Primary
    public PlatformTransactionManager tenantTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "controlTransactionManager")
    public PlatformTransactionManager controlTransactionManager(
            @Qualifier("controlDataSource") DataSource controlDataSource) {
        return new DataSourceTransactionManager(controlDataSource);
    }
}
