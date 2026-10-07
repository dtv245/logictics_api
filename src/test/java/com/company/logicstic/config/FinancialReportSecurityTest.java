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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinancialReportSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityConfig.class, Fixtures.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }

    @Test void blocksAnonymousAndDriverAcrossFinancialReportRoutes() throws Exception {
        for (String path : new String[]{"/api/reports/revenue", "/api/customers/customer/balance",
                "/api/reports/financials/monthly", "/api/reports/expenses", "/api/reports/fleet/fuel",
                "/api/reports/fleet/maintenance", "/api/reports/costs/known-operating-cpm",
                "/api/loads/load/financial-summary", "/api/reports/profitability/by-lane",
                "/api/reports/profitability/by-load", "/api/reports/profitability/by-truck"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("driver").roles("DRIVER"))).andExpect(status().isForbidden());
        }
    }
    @Test void permitsAccountant() throws Exception {
        mvc.perform(get("/api/reports/revenue").with(user("accountant").roles("ACCOUNTANT")))
                .andExpect(status().isOk());
    }
    @Test void onlyAccountingRolesMayApproveExpenses() throws Exception {
        String path = "/api/expenses/expense/approve";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(user("driver").roles("DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("payroll").roles("PAYROLL"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("accountant").roles("ACCOUNTANT"))).andExpect(status().isOk());
    }
    @Test void dispatcherAndDriverCannotApproveAccessorials() throws Exception {
        String path = "/api/accessorial-charges/charge/approve";
        mvc.perform(put(path)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(user("dispatcher").roles("DISPATCHER"))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("driver").roles("DRIVER"))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("accountant").roles("ACCOUNTANT"))).andExpect(status().isOk());
    }

    @Configuration @EnableWebMvc static class Fixtures {
        @Bean LarkAuthenticationFilter tokenFilter() { return new LarkAuthenticationFilter(mock(LarkAuthService.class)); }
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe {
        @GetMapping("/api/reports/revenue") String revenue() { return "ok"; }
        @PostMapping("/api/expenses/{id}/approve") String approve() { return "ok"; }
        @PutMapping("/api/accessorial-charges/{id}/approve") String approveAccessorial() { return "ok"; }
    }
}
