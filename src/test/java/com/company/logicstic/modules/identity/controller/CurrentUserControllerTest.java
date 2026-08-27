package com.company.logicstic.modules.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.modules.identity.dto.response.CurrentUserResponse;
import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.shared.config.RestAccessDeniedHandler;
import com.company.logicstic.shared.config.RestAuthenticationEntryPoint;
import com.company.logicstic.shared.config.SecurityConfiguration;
import com.company.logicstic.shared.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurrentUserController.class)
@ImportAutoConfiguration({
  SecurityAutoConfiguration.class,
  ServletWebSecurityAutoConfiguration.class
})
@Import({
  SecurityConfiguration.class,
  RestAuthenticationEntryPoint.class,
  RestAccessDeniedHandler.class,
  GlobalExceptionHandler.class
})
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.security.jwt.issuer=https://issuer.example",
      "app.security.jwt.audience=logisticsx.api",
      "app.security.jwt.jwk-set-uri=https://issuer.example/jwks"
    })
class CurrentUserControllerTest {

  private final MockMvc mockMvc;

  @MockitoBean private CurrentUserService currentUserService;

  CurrentUserControllerTest(@Autowired MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void returnsCurrentIdentityInApiEnvelope() throws Exception {
    UUID employeeId = UUID.randomUUID();
    given(currentUserService.getCurrentUser(any(JwtAuthenticationToken.class)))
        .willReturn(
            new CurrentUserResponse(
                "subject-123", "driver@example.com", "tenant-a", List.of("DRIVER"), employeeId));

    mockMvc
        .perform(
            get("/api/me")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("subject-123")
                                    .claim("email", "driver@example.com")
                                    .claim("tenant", "tenant-a"))
                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.subject").value("subject-123"))
        .andExpect(jsonPath("$.data.email").value("driver@example.com"))
        .andExpect(jsonPath("$.data.tenantId").value("tenant-a"))
        .andExpect(jsonPath("$.data.roles[0]").value("DRIVER"))
        .andExpect(jsonPath("$.data.employeeId").value(employeeId.toString()));

    verify(currentUserService).getCurrentUser(any(JwtAuthenticationToken.class));
  }

  @Test
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    verify(currentUserService, never()).getCurrentUser(any());
  }
}
