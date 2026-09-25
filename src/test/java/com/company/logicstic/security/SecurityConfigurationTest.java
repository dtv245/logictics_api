package com.company.logicstic.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.LogicsticApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = {LogicsticApplication.class, SecurityTestController.class})
@AutoConfigureMockMvc
@ActiveProfiles({"nodb", SecurityConfigurationTest.PROFILE})
@TestPropertySource(
    properties = {
      "app.security.jwt.issuer=https://issuer.example",
      "app.security.jwt.audience=logisticsx.api",
      "app.security.jwt.jwk-set-uri=https://issuer.example/jwks"
    })
class SecurityConfigurationTest {

  /** Activates {@link SecurityTestController}; no other test turns this on. */
  static final String PROFILE = "securitytest";

  private final MockMvc mockMvc;

  SecurityConfigurationTest(@Autowired MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void healthEndpointIsPublic() throws Exception {
    mockMvc
        .perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void approvedOriginReceivesConfiguredCorsHeadersForPreflight() throws Exception {
    mockMvc
        .perform(
            options("/api/loads")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "Authorization, Content-Type, X-Request-Id"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    "Authorization, Content-Type, X-Request-Id"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  @Test
  void approvedOriginReceivesExposedDownloadHeaderWithoutCorsCredentials() throws Exception {
    mockMvc
        .perform(
            get("/api/loads")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  @Test
  void unapprovedOriginIsNotGrantedCorsAccess() throws Exception {
    mockMvc
        .perform(
            options("/api/loads")
                .header(HttpHeaders.ORIGIN, "https://unapproved.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void protectedEndpointRequiresBearerAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/loads"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void invalidPathVariableUsesStableBadRequestEnvelope() throws Exception {
    mockMvc
        .perform(get("/api/test-uuid/not-a-uuid").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void missingRequestParameterUsesStableBadRequestEnvelope() throws Exception {
    mockMvc
        .perform(get("/api/test-required").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void unsupportedMethodUsesStableMethodNotAllowedEnvelope() throws Exception {
    mockMvc
        .perform(put("/api/health"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unsupportedMediaTypeUsesStableEnvelope() throws Exception {
    mockMvc
        .perform(
            post("/api/test-content")
                .with(jwt())
                .contentType(MediaType.TEXT_PLAIN)
                .content("not-json"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void logoutIsNotAFormLoginOrOidcLogoutEndpoint() throws Exception {
    mockMvc
        .perform(post("/logout"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void driverCanReadLoadsButCannotCreateThem() throws Exception {
    var driver = jwt().authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));

    mockMvc.perform(get("/api/loads").with(driver)).andExpect(status().isOk());
    mockMvc
        .perform(post("/api/loads").with(driver))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void driverCanUpdatePickupAndDeliveryStatus() throws Exception {
    var driver = jwt().authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));

    mockMvc.perform(post("/api/loads/load-1/pick-up").with(driver)).andExpect(status().isOk());
    mockMvc.perform(post("/api/loads/load-1/deliver").with(driver)).andExpect(status().isOk());
  }

  @Test
  void onlyOwnerOrSuperAdminCanManageRoles() throws Exception {
    mockMvc
        .perform(
            get("/api/roles").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/roles").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
        .andExpect(status().isOk());
  }
}

/**
 * Stand-in endpoints so the security rules can be asserted without the real controllers, which need
 * a database.
 *
 * <p>The {@code @Profile} is load-bearing. As a plain {@code @RestController} in a scanned test
 * package, this class was picked up by <em>every</em> full-context {@code @SpringBootTest} and
 * collided with the real controllers ("Ambiguous handler methods mapped for '/api/loads'"). Gating
 * it on a profile only this test activates keeps it invisible to the rest of the suite.
 */
@Profile(SecurityConfigurationTest.PROFILE)
@RestController
class SecurityTestController {

  @GetMapping({"/api/loads", "/api/roles"})
  ResponseEntity<Void> read() {
    return ResponseEntity.ok().build();
  }

  @PostMapping({"/api/loads", "/api/loads/{id}/pick-up", "/api/loads/{id}/deliver"})
  ResponseEntity<Void> write() {
    return ResponseEntity.ok().build();
  }

  @GetMapping("/api/test-uuid/{id}")
  ResponseEntity<Void> uuid(@PathVariable UUID id) {
    return ResponseEntity.ok().build();
  }

  @GetMapping("/api/test-required")
  ResponseEntity<Void> required(@RequestParam String value) {
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/api/test-content", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Void> content() {
    return ResponseEntity.ok().build();
  }
}
