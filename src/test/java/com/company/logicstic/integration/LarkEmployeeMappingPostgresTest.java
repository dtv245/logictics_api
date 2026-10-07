package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.integration.lark.auth.LarkAuthClient;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.integration.lark.auth.LarkTokenResponse;
import com.company.logicstic.integration.lark.auth.LarkUserResponse;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LarkUserMappingRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real callback, PostgreSQL constraints and concurrent first-login transactions. */
@SpringBootTest(properties = {"spring.config.import=", "app.tenancy.enabled=false",
        "app.lark.base.enabled=false", "app.lark.default-tenant-id=lark-mapping-test",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@EnabledIfEnvironmentVariable(named = "TASK_DB_URL", matches = "jdbc:postgresql:.*codex_.*")
class LarkEmployeeMappingPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("TASK_DB_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("TASK_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("TASK_DB_PASSWORD"));
    }

    @Autowired WebApplicationContext web;
    @Autowired LarkAuthService login;
    @Autowired LarkUserMappingRepository mappings;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean LarkAuthClient provider;
    @MockitoSpyBean EmployeeRepository employees;
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach void clearTenant() { TenantContext.clear(); }
    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }
    private String id() { return UUID.randomUUID().toString(); }
    private UUID employee(String email, String status) {
        UUID employee = UUID.randomUUID(), role = UUID.randomUUID();
        jdbc.update("insert into tenant_roles(id,name,normalized_name) values (?,'DRIVER','DRIVER')", role);
        jdbc.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,
                    salary_amount,salary_currency,role_id)
                values (?,?,'Lark','Fixture','HOURLY',?,now(),0,'USD',?)
                """, employee, email, status, role);
        return employee;
    }
    private void profile(String open, String union, String email, String enterpriseEmail) {
        var token = new LarkTokenResponse(0, "success", null);
        when(provider.exchangeCodeForToken(anyString(), anyString())).thenReturn(token);
        when(provider.extractUser(token)).thenReturn(new LarkUserResponse(open, union, "lark-user-id",
                email, enterpriseEmail, "Lark Fixture", null, null, null));
    }
    private org.springframework.test.web.servlet.ResultActions callback() throws Exception {
        return mvc().perform(post("/api/auth/lark/callback").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("code", "fixture-code", "state", login.createState("/")))));
    }
    private void mapping(UUID employee, String open, String union, String email) {
        jdbc.update("""
                insert into lark_user_mappings(id,employee_id,open_id,union_id,email,updated_at)
                values (?,?,?,?,?,now())
                """, UUID.randomUUID(), employee, open, union, email);
    }

    @Test void normalizedEmailCreatesPersistentMappingAndTokenWorksThroughFilter() throws Exception {
        String email = id() + "@company.test", open = id(), union = id();
        UUID employee = employee("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", "ACTIVE");
        profile(" " + open + " ", " " + union + " ", " " + email.toUpperCase(java.util.Locale.ROOT) + " ", null);
        var response = callback().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(employee.toString()))
                .andExpect(jsonPath("$.data.roles[0]").value("DRIVER")).andReturn().getResponse();
        var row = jdbc.queryForMap("select * from lark_user_mappings where open_id=?", open);
        assertEquals(employee, row.get("employee_id")); assertEquals(email, row.get("email"));
        assertEquals(union, row.get("union_id")); assertEquals("lark-user-id", row.get("lark_user_id"));
        assertNotNull(row.get("created_at")); assertNotNull(row.get("updated_at"));
        String bearer = json.readTree(response.getContentAsString()).get("data").get("accessToken").asText();
        mvc().perform(get("/api/me").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subject").value(open));
        verify(employees, never()).save(any());
    }

    @Test void existingOpenMappingWinsOverDifferentUnionMappingAndDifferentEmail() throws Exception {
        String open = id(), union = id(), email = id() + "@company.test";
        UUID linked = employee(id() + "@company.test", "ACTIVE"), other = employee(email, "ACTIVE");
        mapping(linked, open, null, "old@company.test"); mapping(other, null, union, email);
        profile(open, union, email, null);
        callback().andExpect(status().isOk()).andExpect(jsonPath("$.data.employeeId").value(linked.toString()));
        verify(employees, never()).findAllByEmailForLarkLogin(anyString());
        verify(employees, never()).save(any());
    }

    @Test void unionMappingWorksWithNewOpenIdAndWithoutEmail() throws Exception {
        String union = id(); UUID linked = employee(id() + "@company.test", "ACTIVE");
        mapping(linked, null, union, "old@company.test");
        profile(id(), union, null, null);
        callback().andExpect(status().isOk()).andExpect(jsonPath("$.data.employeeId").value(linked.toString()));
        verify(employees, never()).findAllByEmailForLarkLogin(anyString());
    }

    @Test void missingEmployeeIsHttp403WithoutMapping() throws Exception {
        String open = id(); profile(open, null, id() + "@missing.test", null);
        forbiddenWithoutMapping(open);
    }

    @Test void duplicateNormalizedEmailsIncludingInactiveEmployeeAreHttp403() throws Exception {
        String email = id() + "@company.test", open = id();
        employee(email, "ACTIVE"); employee(" " + email.toUpperCase(java.util.Locale.ROOT) + " ", "INACTIVE");
        profile(open, null, email, null); forbiddenWithoutMapping(open);
    }

    @Test void inactiveEmployeeAndInactiveExistingMappingAreHttp403() throws Exception {
        String email = id() + "@company.test", open = id(); UUID inactive = employee(email, "INACTIVE");
        profile(open, null, email, null); forbiddenWithoutMapping(open);
        mapping(inactive, open, null, email);
        profile(open, null, id() + "@different.test", null);
        callback().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LARK_EMPLOYEE_NOT_LINKED"));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\r\n"})
    void absentEmailIsHttp403(String email) throws Exception {
        String open = id(); profile(open, null, email, null); forbiddenWithoutMapping(open);
        verify(employees, never()).findAllByEmailForLarkLogin(anyString());
    }

    @Test void enterpriseEmailCanLinkWhenPersonalEmailIsBlank() throws Exception {
        String email = id() + "@company.test", open = id(); UUID employee = employee(email, "ACTIVE");
        profile(open, null, " ", " " + email.toUpperCase(java.util.Locale.ROOT) + " ");
        callback().andExpect(status().isOk()).andExpect(jsonPath("$.data.employeeId").value(employee.toString()));
        assertEquals(email, jdbc.queryForObject("select email from lark_user_mappings where open_id=?", String.class, open));
    }

    @ParameterizedTest @ValueSource(strings = {"open_id", "union_id"})
    void concurrentFirstLoginsPersistOneMappingAndOuterTransactionsStillCommit(String identity) throws Exception {
        String stable = id(), email = id() + "@company.test"; UUID employee = employee(email, "ACTIVE");
        profile(identity.equals("open_id") ? stable : null, identity.equals("union_id") ? stable : null, email, null);
        var barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            // Spring Data's interface proxy has no Java method body for
            // callRealMethod(); load this single fixture through a real JPA query.
            Object result = List.of(employees.findByEmailForLarkLogin(email).orElseThrow());
            barrier.await(10, TimeUnit.SECONDS); // Both lookups missed before either insert.
            return result;
        }).when(employees).findAllByEmailForLarkLogin(email);
        String state = login.createState("/");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var work = (java.util.concurrent.Callable<UUID>) () -> {
                try {
                    return new TransactionTemplate(transactions).execute(status -> {
                        UUID resolved = login.handleCallback("code", state, null, null, null).employeeId();
                        assertEquals(1, jdbc.queryForObject("select count(*) from employees where id=?", Integer.class, employee));
                        assertFalse(status.isRollbackOnly());
                        return resolved;
                    });
                } finally { TenantContext.clear(); }
            };
            var first = pool.submit(work); var second = pool.submit(work);
            assertEquals(employee, first.get(20, TimeUnit.SECONDS));
            assertEquals(employee, second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from lark_user_mappings where " + identity + "=?", Integer.class, stable));
    }

    @Test void v38UpgradeCreatesMappingConstraintsAndPreservesEmployeeAndMigrationChecksums() {
        String database = "codex_lark_upgrade_" + id().replace("-", ""); jdbc.execute("create database " + database);
        var source = new PGSimpleDataSource(); source.setURL(System.getenv("TASK_DB_URL")); source.setDatabaseName(database);
        source.setUser(System.getenv("TASK_DB_USER")); source.setPassword(System.getenv("TASK_DB_PASSWORD"));
        Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").target("38").load().migrate();
        var upgrade = new JdbcTemplate(source);
        var before = upgrade.queryForList("select version,checksum from flyway_schema_history order by installed_rank");
        UUID employee = UUID.randomUUID();
        upgrade.update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                values (?,?,'Upgrade','Fixture','HOURLY','ACTIVE',now(),0,'USD')
                """, employee, id() + "@upgrade.test");
        var latest = Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load(); latest.migrate();
        assertTrue(latest.validateWithResult().validationSuccessful);
        assertEquals(before, upgrade.queryForList("select version,checksum from flyway_schema_history where version::int<=38 order by installed_rank"));
        assertEquals(employee, upgrade.queryForObject("select id from employees where id=?", UUID.class, employee));
        assertEquals(3, upgrade.queryForObject("select count(*) from pg_constraint where conrelid='lark_user_mappings'::regclass and contype in ('u','f')", Integer.class));
        assertEquals(0, upgrade.queryForObject("select count(*) from lark_user_mappings", Integer.class));
    }

    private void forbiddenWithoutMapping(String open) throws Exception {
        callback().andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("LARK_EMPLOYEE_NOT_LINKED"));
        assertEquals(0, jdbc.queryForObject("select count(*) from lark_user_mappings where open_id=?", Integer.class, open));
    }
}
