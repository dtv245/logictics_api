package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT authentication → controller → service → JPA → two physical tenant DBs. */
@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=false", "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named = "TASK_DB_URL", matches = "jdbc:postgresql:.*codex_.*")
class CurrentUserApiPostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired LarkAuthService tokens;
    @Autowired TenantRoutingDataSource routing;
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach void clear() { TenantContext.clear(); }
    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private String token(String subject, String email, String tenant, UUID claimedEmployee) {
        return tokens.createInternalToken(subject, email, tenant, List.of("ACCOUNTANT", "PAYROLL_MANAGER"), "Test identity", claimedEmployee);
    }
    private UUID employee(String tenant, String email) {
        UUID id = UUID.randomUUID();
        new JdbcTemplate(routing.getRegisteredDataSource(tenant).orElseThrow()).update("""
                insert into employees(id,email,first_name,last_name,salary_type,status,joined_date,salary_amount,salary_currency)
                values (?,?,'Test','Identity','HOURLY','ACTIVE',now(),0,'USD')
                """, id, email);
        return id;
    }

    @Test void realLarkJwtPreservesSubjectEmailTenantRolesAndAuthoritativeEmployee() throws Exception {
        String email = UUID.randomUUID() + "@example.test"; UUID employee = employee("identity-a", email);
        var result = mvc().perform(get("/api/me").header("Authorization", "Bearer " + token("lark-open-id", email, "identity-a", employee)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse();
        var body = json.readTree(result.getContentAsString()); var identity = body.get("data");
        assertTrue(body.get("success").asBoolean()); assertEquals("lark-open-id", identity.get("subject").asText());
        assertEquals(email, identity.get("email").asText()); assertEquals("identity-a", identity.get("tenantId").asText());
        assertEquals(employee.toString(), identity.get("employeeId").asText());
        assertEquals(List.of("ACCOUNTANT", "PAYROLL_MANAGER"), json.convertValue(identity.get("roles"), List.class));
        assertEquals(5, identity.size()); assertTrue(TenantContext.getTenantId().isEmpty());
    }

    @Test void sameEmailMapsToOnlyCurrentTenantAndQuerySelectorsCannotChooseAnotherIdentity() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        UUID a = employee("identity-a", email); UUID b = employee("identity-b", email);
        for (String tenant : List.of("identity-a", "identity-b")) {
            var result = mvc().perform(get("/api/me").param("tenantId", "identity-b").param("employeeId", b.toString())
                    .header("Authorization", "Bearer " + token("same-subject", email, tenant, b)))
                    .andExpect(status().isOk()).andReturn().getResponse();
            var identity = json.readTree(result.getContentAsString()).get("data");
            assertEquals(tenant, identity.get("tenantId").asText());
            assertEquals((tenant.equals("identity-a") ? a : b).toString(), identity.get("employeeId").asText());
            assertTrue(TenantContext.getTenantId().isEmpty());
        }
    }

    @Test void unmappedEmployeeIsExplicitNullEvenWhenSignedClaimContainsAnId() throws Exception {
        var result = mvc().perform(get("/api/me").header("Authorization", "Bearer " + token("unmapped-subject",
                UUID.randomUUID() + "@example.test", "identity-a", UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse();
        var identity = json.readTree(result.getContentAsString()).get("data");
        assertTrue(identity.has("employeeId")); assertTrue(identity.get("employeeId").isNull());
    }

    @Test void absentAndInvalidBearerAreNormalized401() throws Exception {
        mvc().perform(get("/api/me").header("X-Request-Id", "identity-test-request"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc().perform(get("/api/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void normalizedOidcJwtHasSameResponseWithoutControllerProviderBranches() throws Exception {
        String email = UUID.randomUUID() + "@example.test"; UUID id = employee("identity-a", email);
        TenantContext.setTenantId("identity-a");
        var principal = Jwt.withTokenValue("already-authenticated-oidc-test").header("typ", "JWT")
                .subject("oidc-stable-subject").claim("email", email).claim("tenant", "identity-a").build();
        var result = mvc().perform(get("/api/me").with(jwt().jwt(principal).authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        var identity = json.readTree(result.getContentAsString()).get("data");
        assertEquals("oidc-stable-subject", identity.get("subject").asText()); assertEquals(id.toString(), identity.get("employeeId").asText());
        assertEquals("DRIVER", identity.get("roles").get(0).asText());
    }

    @Test void runtimeOpenApiPublishesExactIdentityFieldsWithoutSelectors() throws Exception {
        var result = mvc().perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse();
        var api = json.readTree(result.getContentAsString());
        var operation = api.get("paths").get("/api/me").get("get");
        assertNotNull(operation); assertTrue(!operation.has("parameters") || operation.get("parameters").isEmpty());
        assertTrue(operation.get("responses").has("401")); assertTrue(operation.get("responses").has("403"));
        var fields = api.get("components").get("schemas").get("CurrentUserResponse").get("properties");
        assertEquals(5, fields.size());
        for (String field : List.of("subject", "email", "tenantId", "roles", "employeeId")) assertTrue(fields.has(field));
        assertEquals("bearer", api.get("components").get("securitySchemes").get("bearerAuth").get("scheme").asText());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Tenants {
        @Bean TenantRoutingDataSource dataSource() {
            var base = source(System.getenv("TASK_DB_URL")); var admin = new JdbcTemplate(base);
            var routing = new TenantRoutingDataSource();
            for (String tenant : List.of("identity-a", "identity-b")) {
                String database = "codex_identity_" + UUID.randomUUID().toString().replace("-", "");
                admin.execute("create database " + database);
                var source = source(System.getenv("TASK_DB_URL")); source.setDatabaseName(database);
                var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration/tenant").load();
                flyway.migrate(); assertTrue(flyway.validateWithResult().validationSuccessful);
                routing.addTenantDataSource(tenant, source);
            }
            // Retain isolated databases as diagnostics, never drop or migrate the user's working DB.
            return routing;
        }
        private static PGSimpleDataSource source(String url) {
            var source = new PGSimpleDataSource(); source.setURL(url);
            source.setUser(System.getenv("TASK_DB_USER")); source.setPassword(System.getenv("TASK_DB_PASSWORD")); return source;
        }
        @Bean HibernatePropertiesCustomizer tenantHibernateProperties() {
            // Same bootstrap convention as the production TenantJpaConfiguration.
            return properties -> {
                properties.put("hibernate.hbm2ddl.auto", "none");
                properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");
                properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            };
        }
    }
}
