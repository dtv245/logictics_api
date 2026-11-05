package com.company.logicstic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the actual V54 SQL against a disposable PostgreSQL instance and a minimal V53-shaped fixture. */
class EmployeeTypeMigrationPostgresTest {
    private static final String MIGRATION = "/db/migration/tenant/V54__add_employee_type_to_employees.sql";
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("employee_type_test");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void startDatabaseAndPreparePreMigrationSchema() throws IOException {
        DATABASE.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE public.tenant_roles (
                    id UUID PRIMARY KEY,
                    normalized_name TEXT NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE public.employees (
                    id UUID PRIMARY KEY,
                    role_id UUID NULL REFERENCES public.tenant_roles(id)
                )
                """);

        UUID driverRoleId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID officeRoleId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        jdbc.update("INSERT INTO public.tenant_roles (id, normalized_name) VALUES (?, ?)", driverRoleId, "DRIVER");
        jdbc.update("INSERT INTO public.tenant_roles (id, normalized_name) VALUES (?, ?)", officeRoleId, "DISPATCHER");
        jdbc.update("INSERT INTO public.employees (id, role_id) VALUES (?, ?)",
                UUID.fromString("20000000-0000-0000-0000-000000000001"), driverRoleId);
        jdbc.update("INSERT INTO public.employees (id, role_id) VALUES (?, ?)",
                UUID.fromString("20000000-0000-0000-0000-000000000002"), officeRoleId);
        jdbc.update("INSERT INTO public.employees (id, role_id) VALUES (?, NULL)",
                UUID.fromString("20000000-0000-0000-0000-000000000003"));

        var migrationDirectory = Files.createTempDirectory("employee-type-flyway-");
        try (var input = EmployeeTypeMigrationPostgresTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(input).as("tenant V54 migration resource").isNotNull();
            Files.copy(input, migrationDirectory.resolve("V54__add_employee_type_to_employees.sql"));
        }

        Flyway flyway = Flyway.configure()
                .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .defaultSchema("public")
                .baselineVersion("53")
                .cleanDisabled(true)
                .load();
        flyway.baseline();
        flyway.migrate();
    }

    @AfterAll
    static void stopDatabase() {
        DATABASE.stop();
    }

    @Test
    void backfillsDriversAndOfficeStaffAndEnforcesTheEmployeeTypeDomain() throws Exception {
        assertThat(jdbc.queryForObject("SELECT employee_type FROM public.employees WHERE id = ?",
                String.class, UUID.fromString("20000000-0000-0000-0000-000000000001"))).isEqualTo("DRIVER");
        assertThat(jdbc.queryForObject("SELECT employee_type FROM public.employees WHERE id = ?",
                String.class, UUID.fromString("20000000-0000-0000-0000-000000000002"))).isEqualTo("OFFICE_STAFF");
        assertThat(jdbc.queryForObject("SELECT employee_type FROM public.employees WHERE id = ?",
                String.class, UUID.fromString("20000000-0000-0000-0000-000000000003"))).isEqualTo("OFFICE_STAFF");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO public.employees (id, employee_type) VALUES (?, ?)",
                UUID.fromString("20000000-0000-0000-0000-000000000004"), "CONTRACTOR"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO public.employees (id, employee_type) VALUES (?, NULL)",
                UUID.fromString("20000000-0000-0000-0000-000000000005")))
                .isInstanceOf(Exception.class);

        try (Connection connection = DriverManager.getConnection(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
                Statement statement = connection.createStatement();
                ResultSet indexes = statement.executeQuery("""
                        SELECT indexname FROM pg_indexes
                        WHERE schemaname = 'public' AND tablename = 'employees'
                          AND indexname = 'ix_employees_employee_type'
                        """)) {
            assertThat(indexes.next()).isTrue();
        }
    }

    @Test
    void employeeEntityUsesStringEnumAndDefaultsNewRecordsToOfficeStaff() {
        var employee = new Employee();

        assertThat(employee.getEmployeeType()).isEqualTo(EmployeeType.OFFICE_STAFF);
        assertThat(EmployeeType.values()).containsExactly(EmployeeType.OFFICE_STAFF, EmployeeType.DRIVER);
    }
}
