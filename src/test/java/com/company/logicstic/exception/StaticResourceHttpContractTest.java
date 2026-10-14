package com.company.logicstic.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers NoResourceFoundException through a real resource handler rather than a synthetic throw. */
class StaticResourceHttpContractTest {
    @Configuration @EnableWebMvc static class Config implements WebMvcConfigurer {
        @Bean GlobalExceptionHandler errors(){return new GlobalExceptionHandler();}
        @Override public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/missing-test-assets/");}
    }
    @Test void absentStaticResourceKeeps404Envelope() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
            MockMvcBuilders.webAppContextSetup(context).build().perform(get("/assets/missing.txt"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
    }
}
