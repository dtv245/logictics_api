package com.company.logicstic.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LarkUserMappingRepository;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "app.lark.jwt-secret="})
@ActiveProfiles("nodb")
class NoDbProfileTest {
    @Autowired ApplicationContext beans;
    @Autowired WebApplicationContext web;

    @Test void startsWithoutDatabaseRepositoriesFlywayOrSigningKey() throws Exception {
        assertTrue(beans.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(beans.getBeansOfType(Flyway.class).isEmpty());
        assertTrue(beans.getBeansOfType(EmployeeRepository.class).isEmpty());
        assertTrue(beans.getBeansOfType(LarkUserMappingRepository.class).isEmpty());
        var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("disabled"));
        mvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/lark/authorize")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LARK_DISABLED"));
    }
}
