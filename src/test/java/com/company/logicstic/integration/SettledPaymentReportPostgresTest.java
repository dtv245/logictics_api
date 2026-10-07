package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Legacy payment fixtures are inserted at V37, upgraded without rewriting them, then reported via HTTP. */
@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=false",
        "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named = "TASK_DB_URL", matches = "jdbc:postgresql:.*codex_.*")
class SettledPaymentReportPostgresTest {
    @Autowired WebApplicationContext web;
    @Autowired TenantRoutingDataSource routing;
    @Autowired LarkAuthService tokens;
    private record LegacyPayment(String status, String amount, String currency) { }
    private record Fixture(String tenant, UUID load, UUID customer, JdbcTemplate jdbc, List<Map<String, Object>> payments) { }

    @AfterEach void clear() { TenantContext.clear(); }
    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }
    private String bearer(Fixture fixture, String role) {
        return "Bearer " + tokens.createInternalToken("report-reader", "reader@r.test", fixture.tenant(), List.of(role), "Report reader", null);
    }
    private List<Map<String, Object>> history(JdbcTemplate jdbc) {
        return jdbc.queryForList("select id,status,tenant_id,invoice_id,amount_amount,amount_currency,idempotency_key,input_hash,recorded_at,recorded_by_user_id,version from payments order by id");
    }
    private Fixture fixture(List<LegacyPayment> payments) {
        String database = "codex_report_" + UUID.randomUUID().toString().replace("-", "");
        String tenant = "report-" + UUID.randomUUID();
        var source = new PGSimpleDataSource();
        source.setURL(System.getenv("TASK_DB_URL")); source.setUser(System.getenv("TASK_DB_USER"));
        source.setPassword(System.getenv("TASK_DB_PASSWORD"));
        new JdbcTemplate(source).execute("create database " + database); source.setDatabaseName(database);
        Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").target("37").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID customer = UUID.randomUUID(), load = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("insert into customers(id,name,status,is_vat_exempt) values (?,'Legacy Report','ACTIVE',false)", customer);
        jdbc.update("""
                insert into loads(id,name,type,status,distance,is_in_proximity,customer_id,source,is_hazmat,delivery_cost_amount,delivery_cost_currency,
                destination_address_city,destination_address_country,destination_address_line1,destination_address_state,destination_address_zip_code,
                destination_location_latitude,destination_location_longitude,origin_address_city,origin_address_country,origin_address_line1,
                origin_address_state,origin_address_zip_code,origin_location_latitude,origin_location_longitude)
                values (?,'Legacy Report','FTL','DELIVERED',0,false,?,'MANUAL',false,0,'USD',
                'City','US','Address','TX','00000',0,0,'City','US','Address','TX','00000',0,0)
                """, load, customer);
        // Unclassified historical invoice, with a reconciled line: do not invent accepted rating history.
        jdbc.update("""
                insert into invoices(id,type,status,customer_id,load_id,tax_behavior,subtotal_amount,subtotal_currency,
                tax_total_amount,tax_total_currency,total_amount,total_currency)
                values (?,'FREIGHT','ISSUED',?,?,'exclusive',100,'USD',0,'USD',100,'USD')
                """, invoice, customer, load);
        jdbc.update("""
                insert into invoice_line_items(id,invoice_id,description,type,quantity,"order",tax_amount,amount_amount,amount_currency)
                values (?,?,'Historical freight','FREIGHT',1,0,0,100,'USD')
                """, UUID.randomUUID(), invoice);
        for (LegacyPayment payment : payments) {
            jdbc.update("""
                    insert into payments(id,status,tenant_id,invoice_id,amount_amount,amount_currency,
                    billing_address_line1,billing_address_city,billing_address_state,billing_address_zip_code,billing_address_country)
                    values (?,?,?,?,?,?,'Address','City','State','00000','US')
                    """, UUID.randomUUID(), payment.status(), UUID.randomUUID(), invoice, new BigDecimal(payment.amount()), payment.currency());
        }
        var before = history(jdbc);
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load();
        flyway.migrate(); assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.info().pending().length, "All currently available tenant migrations must be applied");
        assertEquals(before, history(jdbc), "Upgrade must preserve historical financial identity and classification");
        routing.addTenantDataSource(tenant, source);
        return new Fixture(tenant, load, customer, jdbc, before);
    }
    private void unchanged(Fixture fixture) {
        assertEquals(fixture.payments(), history(fixture.jdbc()));
        assertEquals(0, fixture.jdbc().queryForObject("select count(*) from payment_command_events", Integer.class));
        assertTrue(TenantContext.getTenantId().isEmpty());
    }
    private void reports(Fixture fixture, int paid) throws Exception {
        mvc().perform(get("/api/reports/revenue").param("loadId", fixture.load().toString())
                        .header("Authorization", bearer(fixture, "ACCOUNTANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalInvoiceAmount").value(100))
                .andExpect(jsonPath("$.data.paidAmount").value(paid)).andExpect(jsonPath("$.data.openBalance").value(100 - paid))
                .andExpect(jsonPath("$.data.subtotalRevenue").value(100)).andExpect(jsonPath("$.data.taxAmount").value(0))
                .andExpect(jsonPath("$.data.currency").value("USD"));
        mvc().perform(get("/api/customers/" + fixture.customer() + "/balance")
                        .header("Authorization", bearer(fixture, "ACCOUNTANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalInvoiced").value(100))
                .andExpect(jsonPath("$.data.totalPaid").value(paid)).andExpect(jsonPath("$.data.openBalance").value(100 - paid))
                .andExpect(jsonPath("$.data.invoiceCount").value(1)).andExpect(jsonPath("$.data.currency").value("USD"));
        unchanged(fixture);
    }

    @Test void upgradedSettledPaymentCountsAsPaidWithoutRewritingHistoricalRows() throws Exception {
        reports(fixture(List.of(new LegacyPayment("SETTLED", "40", "USD"))), 40);
    }

    @Test void allSuccessfulStatusesAreCaseInsensitiveAndPendingCancelledVoidRemainUnpaid() throws Exception {
        reports(fixture(List.of(new LegacyPayment("sEtTlEd", "10", "USD"), new LegacyPayment("COMPLETED", "10", "USD"),
                new LegacyPayment("paid", "10", "USD"), new LegacyPayment("SUCCEEDED", "10", "USD"),
                new LegacyPayment("PENDING", "50", "USD"), new LegacyPayment("CANCELLED", "50", "USD"),
                new LegacyPayment("VOID", "50", "USD"))), 40);
    }

    @Test void settledPaymentCurrencyMismatchUsesExistingReportErrorAndDoesNotMutate() throws Exception {
        var fixture = fixture(List.of(new LegacyPayment("SETTLED", "40", "EUR")));
        for (var request : List.of(get("/api/reports/revenue").param("loadId", fixture.load().toString()),
                get("/api/customers/" + fixture.customer() + "/balance"))) {
            mvc().perform(request.header("Authorization", bearer(fixture, "ACCOUNTANT")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        }
        unchanged(fixture);
    }

    @Test void physicalTenantAndRoleBoundariesStillPrecedeFinancialReporting() throws Exception {
        var a = fixture(List.of(new LegacyPayment("SETTLED", "40", "USD")));
        var b = fixture(List.of(new LegacyPayment("SETTLED", "70", "USD")));
        reports(a, 40); reports(b, 70);
        mvc().perform(get("/api/reports/revenue").param("loadId", b.load().toString())
                        .header("Authorization", bearer(a, "ACCOUNTANT"))).andExpect(status().isNotFound());
        mvc().perform(get("/api/customers/" + b.customer() + "/balance")
                        .header("Authorization", bearer(a, "ACCOUNTANT"))).andExpect(status().isNotFound());
        mvc().perform(get("/api/reports/revenue").param("loadId", a.load().toString())
                        .header("Authorization", bearer(a, "DRIVER"))).andExpect(status().isForbidden());
        mvc().perform(get("/api/customers/" + a.customer() + "/balance")).andExpect(status().isUnauthorized());
        unchanged(a); unchanged(b);
    }
}
