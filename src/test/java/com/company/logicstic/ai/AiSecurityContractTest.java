package com.company.logicstic.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for the authorization boundary around AI dispatch.
 *
 * <p>AI run/approve/reject are internal operations actions. They must never be
 * reachable by a customer-facing or driver principal, and they must not fall
 * through to the authenticated-only default rule.</p>
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai_security_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false",
    "app.tenancy.enabled=false",
    "app.lark.base.enabled=false"
})
class AiSecurityContractTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("unauthenticated request to AI endpoints is rejected with 401")
    void anonymousIsRejected() throws Exception {
        mvc().perform(get("/api/ai/config"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DRIVER cannot read AI dispatch configuration")
    void driverCannotReadAiConfig() throws Exception {
        mvc().perform(get("/api/ai/config").with(user("driver@example.test").roles("DRIVER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ACCOUNTANT cannot reach AI dispatch either")
    void accountantCannotReadAiConfig() throws Exception {
        mvc().perform(get("/api/ai/config").with(user("accountant@example.test").roles("ACCOUNTANT")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DRIVER cannot approve or reject AI suggestions")
    void driverCannotApproveOrReject() throws Exception {
        UUID decisionId = UUID.randomUUID();
        mvc().perform(post("/api/ai/dispatch/decisions/{id}/approve", decisionId)
                .with(user("driver@example.test").roles("DRIVER")))
            .andExpect(status().isForbidden());
        mvc().perform(post("/api/ai/dispatch/decisions/{id}/reject", decisionId)
                .contentType("application/json")
                .content("{\"reason\":\"not allowed\"}")
                .with(user("driver@example.test").roles("DRIVER")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DISPATCHER can read AI dispatch configuration")
    void dispatcherCanReadAiConfig() throws Exception {
        mvc().perform(get("/api/ai/config").with(user("dispatcher@example.test").roles("DISPATCHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("ADMIN can read AI dispatch configuration")
    void adminCanReadAiConfig() throws Exception {
        mvc().perform(get("/api/ai/config").with(user("admin@example.test").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
