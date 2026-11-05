package com.company.logicstic.integration.lark.event;

import com.company.logicstic.config.SecurityConfig;
import com.company.logicstic.integration.lark.auth.LarkAuthService;
import com.company.logicstic.integration.lark.auth.LarkAuthenticationFilter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LarkEventEndpointSecurityTest {
  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  static class Fixtures {
    @Bean LarkAuthService auth() { return mock(LarkAuthService.class); }
    @Bean LarkAuthenticationFilter authenticationFilter(LarkAuthService service) { return new LarkAuthenticationFilter(service); }
    @Bean LarkEventDispatcher dispatcher() { return mock(LarkEventDispatcher.class); }
    @Bean LarkEventSecurity eventSecurity() { return new LarkEventSecurity(properties(TOKEN, ""), JSON); }
    @Bean LarkEventController controller(LarkEventSecurity security, LarkEventDispatcher dispatcher) {
      return new LarkEventController(security, dispatcher);
    }
  }

  @Test void onlyPostEventRouteIsPublicAndStillRequiresProviderAuthentication() throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
          "test", Map.of("app.security.allowed-origins", "")));
      context.register(SecurityConfig.class, Fixtures.class);
      context.refresh();
      var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
      mvc.perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON).content(challenge(TOKEN)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.challenge").value("challenge-test-đúng"));
      mvc.perform(post("/api/lark/events").contentType(MediaType.APPLICATION_JSON).content(challenge("incorrect")))
          .andExpect(status().isUnauthorized());
      mvc.perform(get("/api/lark/events")).andExpect(status().isUnauthorized());
      mvc.perform(post("/api/lark/events/other")).andExpect(status().isUnauthorized());
      mvc.perform(get("/api/loads")).andExpect(status().isUnauthorized());
      verifyNoInteractions(context.getBean(LarkAuthService.class));
    }
  }
}
