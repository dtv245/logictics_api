package com.company.logicstic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The security chain must use the same ID/envelope as MVC failures. */
class SecurityErrorEnvelopeTest {
    @Test void anonymousAndForbiddenResponsesShareValidatedCorrelation() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(SecurityConfig.class,SecurityAccessControlTest.Fixtures.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).apply(springSecurity()).build();
            mvc.perform(get("/api/roles").header("X-Request-Id","security-contract"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.meta.requestId").value("security-contract"))
                    .andExpect(header().string("X-Request-Id","security-contract"));
            mvc.perform(get("/api/roles").with(user("driver").roles("DRIVER")).header("X-Request-Id","security-contract"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.meta.requestId").value("security-contract"))
                    .andExpect(header().string("X-Request-Id","security-contract"));
        }
    }
}
