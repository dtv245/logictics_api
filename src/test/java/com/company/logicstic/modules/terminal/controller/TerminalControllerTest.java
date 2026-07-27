package com.company.logicstic.modules.terminal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest;
import com.company.logicstic.modules.terminal.dto.response.TerminalResponse;
import com.company.logicstic.modules.terminal.enums.TerminalType;
import com.company.logicstic.modules.terminal.service.TerminalService;
import com.company.logicstic.shared.config.RestAccessDeniedHandler;
import com.company.logicstic.shared.config.RestAuthenticationEntryPoint;
import com.company.logicstic.shared.config.SecurityConfiguration;
import com.company.logicstic.shared.exception.ConflictException;
import com.company.logicstic.shared.exception.GlobalExceptionHandler;
import com.company.logicstic.shared.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller slice test for {@link TerminalController}.
 *
 * <p>Reference for {@code docs/docs/development/engineering-conventions.md} §15.3: this layer
 * asserts HTTP status codes, the response envelope, Bean Validation wiring, method-level
 * authorisation and error translation — never business rules, which are unit-tested against the
 * service.
 *
 * <p>Boot 4 imports: {@code org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest} and
 * {@code org.springframework.test.context.bean.override.mockito.MockitoBean}. The Boot 3
 * {@code @MockBean} and the old {@code …test.autoconfigure.web.servlet} package no longer exist —
 * see {@code docs/docs/development/engineering-conventions.md} §12.
 *
 * <p>{@code @WebMvcTest} loads no {@code @Configuration} of its own, so the production {@link
 * SecurityConfiguration} is imported explicitly together with the two security auto-configurations
 * that supply the {@code HttpSecurity} builder. Without them the slice would run with no filter
 * chain and happily return 200 for an anonymous request — a security test that always passes. With
 * them, the 401/403 cases below exercise the same path matchers and {@code @PreAuthorize} rules as
 * production.
 *
 * <p>{@code @ActiveProfiles("test")} is mandatory, not decorative: {@code application.yml} imports
 * {@code .env}, and {@code .env.example} ships {@code SPRING_PROFILES_ACTIVE=nodb}. Without an
 * explicit profile the slice inherits {@code nodb}, every {@code @Profile("!nodb")} controller is
 * excluded from the context, and each request silently resolves to the static-resource handler with
 * a 404 instead of failing loudly.
 */
@WebMvcTest(TerminalController.class)
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
@DisplayName("/api/terminals")
class TerminalControllerTest {

  private static final String DISPATCHER = "ROLE_DISPATCHER";
  private static final String DRIVER = "ROLE_DRIVER";

  private final MockMvc mockMvc;

  @MockitoBean private TerminalService terminalService;

  TerminalControllerTest(@Autowired MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void should_return_created_with_envelope_when_payload_is_valid() throws Exception {
    // given
    given(terminalService.create(any(CreateTerminalRequest.class))).willReturn(response());

    // when / then
    mockMvc
        .perform(
            post("/api/terminals")
                .with(jwt().authorities(new SimpleGrantedAuthority(DISPATCHER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("USNYC")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.code").value("USNYC"))
        .andExpect(jsonPath("$.data.type").value("SEA_PORT"));
  }

  @Test
  void should_return_field_error_when_unlocode_format_is_invalid() throws Exception {
    // when / then — validation must reject the request before the service is reached
    mockMvc
        .perform(
            post("/api/terminals")
                .with(jwt().authorities(new SimpleGrantedAuthority(DISPATCHER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("NYC")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("code"));

    verify(terminalService, never()).create(any());
  }

  @Test
  void should_return_conflict_when_service_reports_duplicate_code() throws Exception {
    // given
    willThrow(new ConflictException("Terminal with code 'USNYC' already exists"))
        .given(terminalService)
        .create(any(CreateTerminalRequest.class));

    // when / then
    mockMvc
        .perform(
            post("/api/terminals")
                .with(jwt().authorities(new SimpleGrantedAuthority(DISPATCHER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("USNYC")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void should_return_not_found_when_terminal_does_not_exist() throws Exception {
    // given
    UUID id = UUID.randomUUID();
    willThrow(new ResourceNotFoundException("Terminal not found: " + id))
        .given(terminalService)
        .getById(eq(id));

    // when / then
    mockMvc
        .perform(
            get("/api/terminals/{id}", id)
                .with(jwt().authorities(new SimpleGrantedAuthority(DRIVER))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void should_return_unauthorized_when_token_is_missing() throws Exception {
    mockMvc.perform(get("/api/terminals")).andExpect(status().isUnauthorized());

    verify(terminalService, never())
        .search(
            any(),
            any(),
            any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            any(),
            org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void should_return_forbidden_when_driver_tries_to_create_terminal() throws Exception {
    // when / then — read access is granted to drivers, write access is not
    mockMvc
        .perform(
            post("/api/terminals")
                .with(jwt().authorities(new SimpleGrantedAuthority(DRIVER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody("USNYC")))
        .andExpect(status().isForbidden());

    verify(terminalService, never()).create(any());
  }

  @Test
  void should_reject_page_size_above_the_allowed_maximum() throws Exception {
    mockMvc
        .perform(
            get("/api/terminals")
                .with(jwt().authorities(new SimpleGrantedAuthority(DISPATCHER)))
                .param("pageSize", "1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private static TerminalResponse response() {
    return new TerminalResponse(
        UUID.randomUUID(),
        "Port of New York and New Jersey",
        "USNYC",
        "US",
        TerminalType.SEA_PORT,
        null,
        "1 Terminal Way",
        null,
        "Newark",
        "NJ",
        "07114",
        "US",
        OffsetDateTime.now(),
        null);
  }

  private static String validBody(String code) {
    return """
        {
          "name": "Port of New York and New Jersey",
          "code": "%s",
          "countryCode": "US",
          "type": "SEA_PORT",
          "addressLine1": "1 Terminal Way",
          "addressCity": "Newark",
          "addressState": "NJ",
          "addressZipCode": "07114",
          "addressCountry": "US"
        }
        """
        .formatted(code);
  }
}
