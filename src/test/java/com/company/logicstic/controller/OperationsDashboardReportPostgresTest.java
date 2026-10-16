package com.company.logicstic.controller;

import com.company.logicstic.repository.ExpenseRepository;
import com.company.logicstic.repository.InvoiceRepository;
import com.company.logicstic.repository.LoadExceptionRepository;
import com.company.logicstic.repository.MaintenanceRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
    "spring.config.import=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/tenant",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.open-in-view=false",
    "app.tenancy.enabled=false",
    "app.lark.base.enabled=false"
})
class OperationsDashboardReportPostgresTest {

    private static final PGSimpleDataSource DATABASE = database();

    private static PGSimpleDataSource database() {
        var source = new PGSimpleDataSource();
        String url = System.getenv("TASK_DB_URL");
        if (url != null) {
            source.setURL(url);
            source.setUser(System.getenv("TASK_DB_USER"));
            source.setPassword(System.getenv("TASK_DB_PASSWORD"));
        } else {
            var container = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("codex_remediation");
            container.start();
            source.setURL(container.getJdbcUrl());
            source.setUser(container.getUsername());
            source.setPassword(container.getPassword());
        }
        return source;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getURL);
        properties.add("spring.datasource.username", DATABASE::getUser);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private MaintenanceRecordRepository maintenanceRecordRepository;
    @Autowired private LoadExceptionRepository loadExceptionRepository;
    @Autowired private ReportController reportController;

    @Test
    @DisplayName("Verify repositories execute findByPeriod and findByFilters on PostgreSQL without 42P18 error")
    void testRepositoriesExecuteCleanlyOnPostgres() {
        OffsetDateTime from = OffsetDateTime.of(2026, 9, 16, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2026, 10, 16, 23, 59, 59, 0, ZoneOffset.UTC);

        // Test with non-null values
        assertNotNull(invoiceRepository.findByPeriod(from, to));
        assertNotNull(expenseRepository.findApprovedExpenses("FUEL", from, to));
        assertNotNull(expenseRepository.findApprovedExpenses(null, from, to));
        assertNotNull(expenseRepository.findByFilters(null, null, from, to));
        assertNotNull(maintenanceRecordRepository.findByFilters(null, from, to));
        assertNotNull(loadExceptionRepository.findByPeriod(from, to));

        // Test with null values
        assertNotNull(invoiceRepository.findByPeriod(null, null));
        assertNotNull(expenseRepository.findApprovedExpenses(null, null, null));
        assertNotNull(expenseRepository.findByFilters(null, null, null, null));
        assertNotNull(maintenanceRecordRepository.findByFilters(null, null, null));
        assertNotNull(loadExceptionRepository.findByPeriod(null, null));
    }

    @Test
    @DisplayName("Verify monthlyFinancials and report endpoints execute cleanly on PostgreSQL without 500 errors")
    void testReportEndpointsOnPostgres() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        // 1. monthlyFinancials (was throwing 500 due to Invoice & Maintenance queries)
        var monthlyResp = reportController.getMonthlyFinancials(2026, 10, "USD", request);
        assertEquals(200, monthlyResp.getStatusCode().value());
        assertNotNull(monthlyResp.getBody());
        assertEquals("OK", monthlyResp.getBody().code());

        java.time.LocalDate from = java.time.LocalDate.of(2026, 9, 16);
        java.time.LocalDate to = java.time.LocalDate.of(2026, 10, 16);

        // 2. expenses
        var expResp = reportController.getExpensesReport(from, to, "USD", request);
        assertEquals(200, expResp.getStatusCode().value());

        // 3. known-operating-cpm
        var cpmResp = reportController.getKnownOperatingCpm(from, to, "USD", request);
        assertEquals(200, cpmResp.getStatusCode().value());

        // 4. fleet/fuel
        var fuelResp = reportController.getFuelReport(from, to, "USD", request);
        assertEquals(200, fuelResp.getStatusCode().value());

        // 5. fleet/maintenance
        var maintResp = reportController.getMaintenanceReport(from, to, null, "USD", request);
        assertEquals(200, maintResp.getStatusCode().value());

        // 6. operations/exceptions-summary
        var excResp = reportController.getExceptionsSummary(from, to, request);
        assertEquals(200, excResp.getStatusCode().value());

        // 7. operations/on-time-delivery
        var otdResp = reportController.getOtdReport(from, to, request);
        assertEquals(200, otdResp.getStatusCode().value());

        // 8. operations/delays
        var delaysResp = reportController.getDelayReport(from, to, request);
        assertEquals(200, delaysResp.getStatusCode().value());

        // 9. operations/transit-time
        var transitResp = reportController.getTransitTimeReport(from, to, request);
        assertEquals(200, transitResp.getStatusCode().value());

        // 10. drivers/ranking
        var rankResp = reportController.getDriverRanking(from, to, "USD", "score", 5, request);
        assertEquals(200, rankResp.getStatusCode().value());
        assertNotNull(rankResp.getBody());
        assertEquals("OK", rankResp.getBody().code());
    }
}
