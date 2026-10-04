package com.company.logicstic.integration.lark.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LarkAuthControllerTest {

  private MockMvc mockMvc;

  @Mock private LarkAuthService authService;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new LarkAuthController(authService)).build();
  }

  @Test
  @DisplayName("GET /api/auth/lark/authorize should return 200 with authorizationUrl")
  void getAuthorizeUrl_success() throws Exception {
    given(authService.getAuthorizeUrl(any())).willReturn("https://open.larksuite.com/open-apis/authen/v1/authorize?app_id=123");

    mockMvc
        .perform(get("/api/auth/lark/authorize").param("returnTo", "/dashboard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("LARK_AUTHORIZE_URL"))
        .andExpect(jsonPath("$.data.authorizationUrl").value("https://open.larksuite.com/open-apis/authen/v1/authorize?app_id=123"));
  }

  @Test
  @DisplayName("GET /api/auth/lark/login should redirect (302) to Lark authorization URL")
  void login_redirect_success() throws Exception {
    given(authService.getAuthorizeUrl(any())).willReturn("https://open.larksuite.com/open-apis/authen/v1/authorize?app_id=123");

    mockMvc
        .perform(get("/api/auth/lark/login").param("returnTo", "/dashboard"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://open.larksuite.com/open-apis/authen/v1/authorize?app_id=123"));
  }

  @Test
  @DisplayName("POST /api/auth/lark/callback should return 200 with JWT and user information")
  void callback_success() throws Exception {
    LarkAuthService.LarkLoginResult mockResult =
        new LarkAuthService.LarkLoginResult(
            "mock.jwt.token",
            "Bearer",
            28800L,
            "ou_123",
            "john@company.com",
            "test-tenant",
            List.of("DISPATCHER"),
            "/dashboard",
            "John Doe",
            UUID.randomUUID());

    given(authService.handleCallback(any(), any(), any(), any(), any())).willReturn(mockResult);

    String payload =
        """
        {
          "code": "auth_code_123",
          "state": "valid_state_123"
        }
        """;

    mockMvc
        .perform(post("/api/auth/lark/callback").contentType(MediaType.APPLICATION_JSON).content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("LARK_AUTHENTICATED"))
        .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"))
        .andExpect(jsonPath("$.data.email").value("john@company.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("DISPATCHER"));
  }
}
