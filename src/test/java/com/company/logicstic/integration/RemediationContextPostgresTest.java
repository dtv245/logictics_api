package com.company.logicstic.integration;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Real production persistence semantics, independently of the H2 smoke test. */
@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/tenant", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false", "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
class RemediationContextPostgresTest {
    private static final PGSimpleDataSource DATABASE = database();
    private static PGSimpleDataSource database() {
        var source = new PGSimpleDataSource();
        String url = System.getenv("TASK_DB_URL");
        if (url != null) {
            if (!url.matches("jdbc:postgresql://[^/]+/codex_[a-z0-9_]+"))
                throw new IllegalStateException("Explicit disposable PostgreSQL database required");
            source.setURL(url); source.setUser(System.getenv("TASK_DB_USER")); source.setPassword(System.getenv("TASK_DB_PASSWORD"));
        } else {
            var container = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("codex_remediation");
            container.start();
            source.setURL(container.getJdbcUrl()); source.setUser(container.getUsername()); source.setPassword(container.getPassword());
        }
        return source;
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getURL);
        properties.add("spring.datasource.username", DATABASE::getUser);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;

    @Test void fullContextMigratesAndValidatesPostgresSchema() {
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals("PostgreSQL", jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName()));
        assertTrue(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class) >= 37);
    }

    @Test void populatedV35AndV36UpgradeWithoutRewritingHistory() {
        for (String baseline : new String[]{"35", "36", "37"}) {
            String name = "codex_upgrade_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("create database " + name);
            var source = new PGSimpleDataSource(); source.setURL(DATABASE.getURL()); source.setDatabaseName(name);
            source.setUser(DATABASE.getUser()); source.setPassword(DATABASE.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").target(baseline).load().migrate();
            var fixture = new JdbcTemplate(source); UUID employee = UUID.randomUUID();
            fixture.update("""
                    insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                    values (?,?,'Upgrade','Fixture','HOURLY','ACTIVE',now(),0,'USD')
                    """, employee, employee + "@upgrade.invalid");
            UUID invoice = UUID.randomUUID(), payment = UUID.randomUUID(), tenant = UUID.randomUUID();
            fixture.update("insert into invoices(id,type,status,tax_behavior,subtotal_amount,subtotal_currency,tax_total_amount,tax_total_currency,total_amount,total_currency) values (?,'FREIGHT','ISSUED','exclusive',100,'USD',0,'USD',100,'USD')", invoice);
            fixture.update("insert into payments(id,status,tenant_id,invoice_id,amount_amount,amount_currency,billing_address_line1,billing_address_city,billing_address_state,billing_address_zip_code,billing_address_country) values (?,'COMPLETED',?,?,10,'USD','Address','City','State','00000','US')", payment, tenant, invoice);
            var financialBefore = fixture.queryForMap("select id,status,tenant_id,invoice_id,amount_amount,amount_currency,recorded_at,recorded_by_user_id from payments where id=?", payment);
            var before = fixture.queryForList("select version, checksum from flyway_schema_history where success order by installed_rank");
            var latest = Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load();
            latest.migrate(); assertTrue(latest.validateWithResult().validationSuccessful);
            assertEquals(before, fixture.queryForList("select version, checksum from flyway_schema_history where success and version::int <= ? order by installed_rank", Integer.parseInt(baseline)));
            assertEquals(employee, fixture.queryForObject("select id from employees where id=?", UUID.class, employee));
            assertEquals(financialBefore, fixture.queryForMap("select id,status,tenant_id,invoice_id,amount_amount,amount_currency,recorded_at,recorded_by_user_id from payments where id=?", payment));
            assertEquals(0, fixture.queryForObject("select count(*) from payment_command_events where payment_id=?", Integer.class, payment));
            assertEquals(3, fixture.queryForObject("select count(*) from information_schema.columns where table_name in ('loads','trips','trucks') and column_name='version'", Integer.class));
        }
    }
}
