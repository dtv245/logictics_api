package com.company.logicstic.integration;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.config.TenantRoutingDataSource;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import java.util.List;
import java.util.UUID;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/JWT/JPA queries against two physical PostgreSQL tenant databases. */
@SpringBootTest(properties = {"spring.config.import=", "spring.flyway.enabled=false",
        "app.tenancy.enabled=false", "app.lark.base.enabled=false"})
@Import(CurrentUserApiPostgresTest.Tenants.class)
@EnabledIfEnvironmentVariable(named = "TASK_DB_URL", matches = "jdbc:postgresql:.*codex_.*")
class CustomerSearchPostgresTest {
    @Autowired WebApplicationContext web;
    @Autowired TenantRoutingDataSource routing;
    @Autowired LarkAuthService tokens;

    @AfterEach void clear() { TenantContext.clear(); }
    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }
    private JdbcTemplate db(String tenant) { return new JdbcTemplate(routing.getRegisteredDataSource(tenant).orElseThrow()); }
    private String bearer(String tenant) {
        return "Bearer " + tokens.createInternalToken("customer-reader", "reader@c.test", tenant,
                List.of("ACCOUNTANT"), "Customer reader", null);
    }
    private UUID customer(String tenant, String name, String email, String status) {
        UUID id = UUID.randomUUID();
        db(tenant).update("insert into customers(id,name,email,status,is_vat_exempt) values (?,?,?,?,false)", id, name, email, status);
        return id;
    }

    @Test void omittedSearchSupportsDefaultListAndSortedPaginationWithAccurateTotals() throws Exception {
        String status = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) customer("identity-a", "Customer " + i, "page" + i + "@c.test", status);
        long total = db("identity-a").queryForObject("select count(*) from customers", Long.class);
        mvc().perform(get("/api/customers").header("Authorization", bearer("identity-a")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(total));
        mvc().perform(get("/api/customers").header("Authorization", bearer("identity-a"))
                        .param("status", status).param("pageSize", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3)).andExpect(jsonPath("$.data.currentPage").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].name").value("Customer 0"));
        mvc().perform(get("/api/customers").header("Authorization", bearer("identity-a"))
                        .param("status", status).param("pageSize", "2").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(5))
                .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].name").value("Customer 4"));
    }

    @Test void emptySearchAndCaseInsensitiveNameOrEmailSearchRetainStatusFiltering() throws Exception {
        String state = UUID.randomUUID().toString();
        UUID first = customer("identity-a", "Alpha Transport", "dispatch@c.test", state);
        UUID second = customer("identity-a", "Other Carrier", "UniqueBilling@c.test", state);
        customer("identity-a", "Alpha excluded", "excluded@c.test", UUID.randomUUID().toString());
        mvc().perform(get("/api/customers").header("Authorization", bearer("identity-a"))
                        .param("search", "").param("status", state))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(2));
        for (var selector : List.of(new String[]{"ALPHA", first.toString()}, new String[]{"uniquebilling", second.toString()})) {
            mvc().perform(get("/api/customers").header("Authorization", bearer("identity-a"))
                            .param("search", selector[0]).param("status", state))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                    .andExpect(jsonPath("$.data.items[0].id").value(selector[1]));
        }
    }

    @Test void omittedAndNonemptySearchCannotReadAnotherPhysicalTenant() throws Exception {
        String state = UUID.randomUUID().toString();
        UUID a = customer("identity-a", "Shared Customer A", "shared@c.test", state);
        UUID b = customer("identity-b", "Shared Customer B", "shared@c.test", state);
        for (String tenant : List.of("identity-a", "identity-b")) {
            for (boolean searched : List.of(false, true)) {
                var request = get("/api/customers").header("Authorization", bearer(tenant)).param("status", state)
                        .param("tenantId", "identity-b");
                if (searched) request.param("search", "SHARED");
                mvc().perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(1))
                        .andExpect(jsonPath("$.data.items[0].id").value((tenant.equals("identity-a") ? a : b).toString()));
                assertTrue(TenantContext.getTenantId().isEmpty());
            }
        }
        mvc().perform(get("/api/customers/" + b).header("Authorization", bearer("identity-a"))).andExpect(status().isNotFound());
        mvc().perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }
}
