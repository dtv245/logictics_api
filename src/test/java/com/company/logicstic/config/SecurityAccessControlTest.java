package com.company.logicstic.config;

import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.integration.lark.auth.LarkAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityAccessControlTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "cors-fixture", java.util.Map.of("app.security.allowed-origins", "https://client.example.test")));
        context.register(SecurityConfig.class, Fixtures.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void blocksAnonymousAccessToProtectedCoreEndpoints() throws Exception {
        String[] protectedPaths = {
                "/api/customers",
                "/api/loads",
                "/api/payments",
                "/api/trips",
                "/api/trucks",
                "/api/drivers",
                "/api/employees",
                "/api/roles",
                "/api/documents",
                "/api/messages",
                "/api/notifications",
                "/api/inspections"
        };
        for (String path : protectedPaths) {
            mvc.perform(get(path).header("X-Request-Id", "security-test"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.meta.requestId").value("security-test"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void allowsAnonymousAccessToPublicEndpoints() throws Exception {
        String[] publicPaths = {
                "/actuator/health",
                "/health",
                "/api/health",
                "/v3/api-docs",
                "/swagger-ui/index.html",
                "/swagger-ui.html",
                "/api/auth/lark/login"
        };
        for (String path : publicPaths) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/payroll/provider-callbacks/ping")).andExpect(status().isOk());
    }

    @Test void publicPathsAreRestrictedToTheirApprovedMethods() throws Exception {
        mvc.perform(post("/api/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payroll/provider-callbacks/ping")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/lark/new-operation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/lark/callback")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/future-business-route")).andExpect(status().isUnauthorized());
    }

    @Test void corsPreflightDoesNotAuthorizeTheActualBusinessRequest() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/payments")
                .header("Origin", "https://client.example.test").header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "https://client.example.test"));
        mvc.perform(post("/api/payments").header("Origin", "https://client.example.test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/payments")
                .header("Origin", "https://untrusted.example.test").header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test void driverCannotReadPaymentsAndReceivesNormalized403() throws Exception {
        mvc.perform(get("/api/payments").with(user("driver").roles("DRIVER")))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void enforcesRoleBasedAccessControlForAdminEndpoints() throws Exception {
        // Driver cannot access role management
        mvc.perform(get("/api/roles").with(user("driver").roles("DRIVER")))
                .andExpect(status().isForbidden());

        // Dispatcher cannot access role management
        mvc.perform(get("/api/roles").with(user("dispatcher").roles("DISPATCHER")))
                .andExpect(status().isForbidden());

        // Admin can access role management
        mvc.perform(get("/api/roles").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void permitsDispatcherToAccessOperationsEndpoints() throws Exception {
        mvc.perform(get("/api/loads").with(user("dispatcher").roles("DISPATCHER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/trips").with(user("dispatcher").roles("DISPATCHER")))
                .andExpect(status().isOk());
    }

    @Test
    void permitsAccountantToAccessBillingEndpoints() throws Exception {
        mvc.perform(get("/api/payments").with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/invoices").with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    static class Fixtures {
        @Bean
        LarkAuthenticationFilter tokenFilter() {
            return new LarkAuthenticationFilter(mock(LarkAuthService.class));
        }

        @Bean
        ProbeController probe() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/actuator/health") String actuatorHealth() { return "ok"; }
        @GetMapping("/health") String health() { return "ok"; }
        @GetMapping("/api/health") String apiHealth() { return "ok"; }
        @GetMapping("/v3/api-docs") String apiDocs() { return "ok"; }
        @GetMapping("/swagger-ui/index.html") String swaggerUi() { return "ok"; }
        @GetMapping("/swagger-ui.html") String swaggerHtml() { return "ok"; }
        @GetMapping("/api/auth/lark/login") String larkLogin() { return "ok"; }
        @PostMapping("/api/payroll/provider-callbacks/ping") String payrollCallback() { return "ok"; }

        @GetMapping("/api/roles") String roles() { return "ok"; }
        @GetMapping("/api/employees") String employees() { return "ok"; }
        @GetMapping("/api/drivers") String drivers() { return "ok"; }
        @GetMapping("/api/customers") String customers() { return "ok"; }
        @GetMapping("/api/trucks") String trucks() { return "ok"; }
        @GetMapping("/api/loads") String loads() { return "ok"; }
        @GetMapping("/api/trips") String trips() { return "ok"; }
        @GetMapping("/api/documents") String documents() { return "ok"; }
        @GetMapping("/api/inspections") String inspections() { return "ok"; }
        @GetMapping("/api/payments") String payments() { return "ok"; }
        @GetMapping("/api/invoices") String invoices() { return "ok"; }
        @GetMapping("/api/messages") String messages() { return "ok"; }
        @GetMapping("/api/notifications") String notifications() { return "ok"; }
    }
}
