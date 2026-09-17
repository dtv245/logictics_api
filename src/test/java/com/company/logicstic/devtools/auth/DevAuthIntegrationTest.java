package com.company.logicstic.devtools.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.logicstic.LogicsticApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = {LogicsticApplication.class, DevAuthTestController.class})
@AutoConfigureMockMvc
@ActiveProfiles({"nodb", "dev-auth", DevAuthIntegrationTest.PROFILE})
@TestPropertySource(
    properties = {
      "app.security.jwt.issuer=https://issuer.example",
      "app.security.jwt.audience=logisticsx.api",
      "app.security.jwt.jwk-set-uri=https://issuer.example/jwks",
      "app.dev-auth.username=admin@logicstic.local",
      "app.dev-auth.password=Logicstic@2026",
      "app.dev-auth.access-token=local-integration-token-at-least-32-characters",
      "app.dev-auth.tenant-id=local-development",
      "app.dev-auth.token-ttl=8h"
    })
class DevAuthIntegrationTest {

  static final String PROFILE = "devauthtest";
  private static final String ACCESS_TOKEN = "local-integration-token-at-least-32-characters";

  private final MockMvc mockMvc;

  DevAuthIntegrationTest(@Autowired MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  @Test
  void validCredentialsReturnAFullAccessBearerToken() throws Exception {
    mockMvc
        .perform(
            post("/api/dev-auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"admin@logicstic.local","password":"Logicstic@2026"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
        .andExpect(jsonPath("$.data.roles[0]").value("SUPERADMIN"));

    mockMvc
        .perform(
            get("/api/dev-auth-test/protected").header("Authorization", "Bearer " + ACCESS_TOKEN))
        .andExpect(status().isOk());
  }

  @Test
  void invalidCredentialsAndTokensAreRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/dev-auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"username":"admin@logicstic.local","password":"wrong-password"}
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

    mockMvc
        .perform(
            get("/api/dev-auth-test/protected")
                .header("Authorization", "Bearer invalid-local-token"))
        .andExpect(status().isUnauthorized());
  }
}

@Profile(DevAuthIntegrationTest.PROFILE)
@RestController
class DevAuthTestController {

  @GetMapping("/api/dev-auth-test/protected")
  ResponseEntity<Void> protectedEndpoint() {
    return ResponseEntity.ok().build();
  }
}
