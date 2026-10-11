package com.company.logicstic.integration.lark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LarkAuthClientTest {

  @Mock private RestTemplate restTemplate;

  private LarkProperties properties;
  private LarkAuthClient authClient;

  @BeforeEach
  void setUp() {
    properties =
        new LarkProperties(
            true,
            "cli_test_app_id",
            "sec_test_app_secret",
            "http://localhost:5173/auth/callback",
            "https://open.larksuite.com",
            "my-super-secret-jwt-key-with-more-than-256-bits-length!",
            "https://open.larksuite.com",
            Duration.ofHours(8),
            Duration.ofMinutes(15),
            "test-tenant");

    authClient = new LarkAuthClient(properties, restTemplate);
  }

  @Test
  @DisplayName("buildAuthorizeUrl properly constructs authorization URL with parameters")
  void buildAuthorizeUrl_constructsValidUrl() {
    String url = authClient.buildAuthorizeUrl("state-123", "http://localhost:5173/auth/callback");

    assertThat(url)
        .contains("https://open.larksuite.com/open-apis/authen/v1/authorize")
        .contains("app_id=cli_test_app_id")
        .contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Fcallback")
        .contains("response_type=code")
        .contains("state=state-123");
  }

  @Test
  @DisplayName("getAppAccessToken fetches from internal endpoint and caches result")
  void getAppAccessToken_fetchesAndCachesToken() {
    var mockTokenResponse =
        new LarkAuthClient.LarkAppTokenResponse(0, "ok", "a-test-app-access-token", 7200L);

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal"),
                eq(HttpMethod.POST),
                argThat(
                    entity -> {
                      @SuppressWarnings("unchecked")
                      Map<String, String> body = (Map<String, String>) entity.getBody();
                      return body != null
                          && "cli_test_app_id".equals(body.get("app_id"))
                          && "sec_test_app_secret".equals(body.get("app_secret"));
                    }),
                eq(LarkAuthClient.LarkAppTokenResponse.class)))
        .willReturn(ResponseEntity.ok(mockTokenResponse));

    String token1 = authClient.getAppAccessToken();
    String token2 = authClient.getAppAccessToken();

    assertThat(token1).isEqualTo("a-test-app-access-token");
    assertThat(token2).isEqualTo("a-test-app-access-token");

    // Only one exchange call should be made due to caching
    verify(restTemplate, times(1))
        .exchange(
            eq("https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal"),
            eq(HttpMethod.POST),
            any(),
            eq(LarkAuthClient.LarkAppTokenResponse.class));
  }

  @Test
  @DisplayName("exchangeCodeForToken includes Bearer app_access_token and body")
  void exchangeCodeForToken_success() {
    var appTokenResponse =
        new LarkAuthClient.LarkAppTokenResponse(0, "ok", "a-valid-app-token", 7200L);
    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal"),
                eq(HttpMethod.POST),
                any(),
                eq(LarkAuthClient.LarkAppTokenResponse.class)))
        .willReturn(ResponseEntity.ok(appTokenResponse));

    var userTokenData =
        new LarkTokenResponse.LarkTokenData(
            "u-user-token",
            "Bearer",
            7200L,
            "ur-refresh",
            2592000L,
            "ou_123",
            "on_123",
            "usr_123",
            "Test User",
            "Test",
            "https://avatar.test",
            "user@company.com",
            null,
            "+123456789");
    var tokenResponse = new LarkTokenResponse(0, "success", userTokenData);

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/authen/v1/access_token"),
                eq(HttpMethod.POST),
                argThat(
                    entity -> {
                      HttpHeaders headers = entity.getHeaders();
                      @SuppressWarnings("unchecked")
                      Map<String, String> body = (Map<String, String>) entity.getBody();
                      return "Bearer a-valid-app-token".equals(headers.getFirst("Authorization"))
                          && body != null
                          && "authorization_code".equals(body.get("grant_type"))
                          && "auth_code_xyz".equals(body.get("code"));
                    }),
                eq(LarkTokenResponse.class)))
        .willReturn(ResponseEntity.ok(tokenResponse));

    LarkTokenResponse result =
        authClient.exchangeCodeForToken("auth_code_xyz", "http://localhost:5173/auth/callback");

    assertThat(result).isNotNull();
    assertThat(result.code()).isEqualTo(0);
    assertThat(result.data().accessToken()).isEqualTo("u-user-token");
    assertThat(result.data().openId()).isEqualTo("ou_123");
  }

  @Test
  @DisplayName("exchangeCodeForToken retries when code 20014 (invalid app_access_token) is returned")
  void exchangeCodeForToken_retriesOn20014() {
    // 1st app token
    var initialAppToken =
        new LarkAuthClient.LarkAppTokenResponse(0, "ok", "a-expired-app-token", 7200L);
    // 2nd fresh app token
    var freshAppToken =
        new LarkAuthClient.LarkAppTokenResponse(0, "ok", "a-fresh-app-token", 7200L);

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal"),
                eq(HttpMethod.POST),
                any(),
                eq(LarkAuthClient.LarkAppTokenResponse.class)))
        .willReturn(ResponseEntity.ok(initialAppToken))
        .willReturn(ResponseEntity.ok(freshAppToken));

    var response20014 = new LarkTokenResponse(20014, "app_access_token check failed", null);

    var successUserData =
        new LarkTokenResponse.LarkTokenData(
            "u-fresh-user-token",
            "Bearer",
            7200L,
            null,
            null,
            "ou_retry_success",
            null,
            null,
            "User",
            null,
            null,
            "user@retry.com",
            null,
            null);
    var successResponse = new LarkTokenResponse(0, "success", successUserData);

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/authen/v1/access_token"),
                eq(HttpMethod.POST),
                argThat(
                    entity ->
                        "Bearer a-expired-app-token"
                            .equals(entity.getHeaders().getFirst("Authorization"))),
                eq(LarkTokenResponse.class)))
        .willReturn(ResponseEntity.ok(response20014));

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/authen/v1/access_token"),
                eq(HttpMethod.POST),
                argThat(
                    entity ->
                        "Bearer a-fresh-app-token"
                            .equals(entity.getHeaders().getFirst("Authorization"))),
                eq(LarkTokenResponse.class)))
        .willReturn(ResponseEntity.ok(successResponse));

    LarkTokenResponse result =
        authClient.exchangeCodeForToken("code_test", "http://localhost:5173/auth/callback");

    assertThat(result).isNotNull();
    assertThat(result.code()).isEqualTo(0);
    assertThat(result.data().openId()).isEqualTo("ou_retry_success");
  }

  @Test
  @DisplayName("exchangeCodeForToken blank code throws BadRequest")
  void exchangeCodeForToken_blankCode_throwsBadRequest() {
    assertThatThrownBy(() -> authClient.exchangeCodeForToken("   ", null))
        .isInstanceOf(ApiException.class)
        .satisfies(
            ex -> {
              ApiException apiEx = (ApiException) ex;
              assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(apiEx.getMessage()).contains("Authorization code must not be blank");
            });
  }

  @Test
  @DisplayName("extractUser falls back to fetchUserInfo when email is missing")
  void extractUser_missingEmail_callsUserInfoEndpoint() {
    var tokenData =
        new LarkTokenResponse.LarkTokenData(
            "u-test-user-token",
            "Bearer",
            7200L,
            null,
            null,
            "ou_fallback",
            "on_fallback",
            "usr_fallback",
            null,
            null,
            null,
            null,
            null,
            null);
    var tokenResponse = new LarkTokenResponse(0, "ok", tokenData);

    var fetchedProfile =
        new LarkUserResponse(
            "ou_fallback",
            "on_fallback",
            "usr_fallback",
            "fetched@company.com",
            null,
            "Fetched Name",
            null,
            null,
            null);
    var userInfoWrapper =
        new LarkAuthClient.LarkUserInfoWrapper(0, "success", fetchedProfile);

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/authen/v1/user_info"),
                eq(HttpMethod.GET),
                argThat(
                    entity ->
                        "Bearer u-test-user-token"
                            .equals(entity.getHeaders().getFirst("Authorization"))),
                eq(LarkAuthClient.LarkUserInfoWrapper.class)))
        .willReturn(ResponseEntity.ok(userInfoWrapper));

    LarkUserResponse user = authClient.extractUser(tokenResponse);

    assertThat(user).isNotNull();
    assertThat(user.email()).isEqualTo("fetched@company.com");
    assertThat(user.name()).isEqualTo("Fetched Name");
    assertThat(user.openId()).isEqualTo("ou_fallback");
  }
}
