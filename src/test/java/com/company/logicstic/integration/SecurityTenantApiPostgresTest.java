package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.integration.lark.config.LarkProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=false", "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named="TASK_DB_URL", matches="jdbc:postgresql:.*codex_.*")
class SecurityTenantApiPostgresTest {
    @Autowired WebApplicationContext context;
    @Autowired LarkAuthService tokens;
    @Autowired LarkProperties properties;
    @Autowired TenantRoutingDataSource routing;
    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    private String token(String tenant, String role) { return tokens.createInternalToken("subject", "security@example.test", tenant, List.of(role), "Security fixture", null); }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void everyVerbRejectsAnonymousBeforeControllerValidationOrPersistence() throws Exception {
        for (var request : List.of(get("/api/payments"), post("/api/payments"), put("/api/payments/"+UUID.randomUUID()), delete("/api/payments/"+UUID.randomUUID())))
            mvc().perform(request.contentType("application/json").content("{malformed").header("X-Request-Id", "auth-first"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.meta.requestId").value("auth-first"));
    }
    @Test void realJwtDriverIsForbiddenAndAccountantReadsOnlyItsRoutedTenant() throws Exception {
        mvc().perform(get("/api/payments").header("Authorization", "Bearer "+token("identity-a", "DRIVER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc().perform(get("/api/payments").param("tenantId", "identity-b").header("Authorization", "Bearer "+token("identity-a", "ACCOUNTANT")))
                .andExpect(status().isOk());
        assertTrue(TenantContext.getTenantId().isEmpty());
    }
    @Test void expiredAndMalformedTokensFail401AndClearTenantBeforeNextRequest() throws Exception {
        String expired=Jwts.builder().subject("subject").issuer(properties.jwtIssuer())
                .claim("email", "security@example.test").claim("tenant", "identity-a")
                .claim("roles", List.of("ADMIN")).expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8))).compact();
        for (String value : List.of(expired, "malformed.token", "")) {
            mvc().perform(get("/api/customers").header("Authorization", "Bearer "+value))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            assertTrue(TenantContext.getTenantId().isEmpty());
        }
        mvc().perform(get("/api/payments").header("Authorization", "Bearer "+token("identity-b", "ACCOUNTANT"))).andExpect(status().isOk());
    }
    @Test void preboundDifferentTenantAndUnknownTenantFailClosed() throws Exception {
        TenantContext.setTenantId("identity-b");
        mvc().perform(get("/api/payments").header("Authorization", "Bearer "+token("identity-a", "ACCOUNTANT")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("IDENTITY_TENANT_MISMATCH"));
        assertTrue(TenantContext.getTenantId().isEmpty());
        // The router has no fallback database. Unknown tenant cannot read either registered tenant.
        mvc().perform(get("/api/payments").header("Authorization", "Bearer "+token("unknown-tenant", "ACCOUNTANT")))
                .andExpect(status().is5xxServerError());
        assertTrue(TenantContext.getTenantId().isEmpty());
    }
    @Test void adminRoleManagementAndUnsignedCallbackContractsAreRetained() throws Exception {
        mvc().perform(get("/api/roles").header("Authorization", "Bearer "+token("identity-a", "DISPATCHER"))).andExpect(status().isForbidden());
        mvc().perform(get("/api/roles").header("Authorization", "Bearer "+token("identity-a", "ADMIN"))).andExpect(status().isOk());
        mvc().perform(post("/api/payroll/provider-callbacks/not-configured").contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
