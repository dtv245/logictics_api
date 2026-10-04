package com.company.logicstic.integration.lark.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.company.logicstic.integration.lark.config.LarkProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LarkBaseTokenProviderTest {

  @Mock private RestTemplate restTemplate;

  private LarkProperties properties;
  private LarkBaseTokenProvider tokenProvider;

  @BeforeEach
  void setUp() {
    properties =
        new LarkProperties(
            true,
            "cli_app_123",
            "sec_secret_123",
            "http://localhost:5173/auth/callback",
            "https://open.larksuite.com",
            "test-secret-at-least-256-bits-long-key!",
            "https://open.larksuite.com",
            Duration.ofHours(8),
            Duration.ofMinutes(15),
            "test-tenant");

    tokenProvider = new LarkBaseTokenProvider(properties, restTemplate);
  }

  @Test
  @DisplayName("getTenantAccessToken fetches and caches token on subsequent calls")
  void getTenantAccessToken_cachesToken() {
    LarkBaseTokenProvider.LarkTenantTokenResponse mockResponse =
        new LarkBaseTokenProvider.LarkTenantTokenResponse(0, "ok", "t-token-test-12345", 7200L);

    given(
            restTemplate.exchange(
                eq("https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"),
                eq(HttpMethod.POST),
                any(),
                eq(LarkBaseTokenProvider.LarkTenantTokenResponse.class)))
        .willReturn(ResponseEntity.ok(mockResponse));

    String firstCall = tokenProvider.getTenantAccessToken();
    String secondCall = tokenProvider.getTenantAccessToken();

    assertThat(firstCall).isEqualTo("t-token-test-12345");
    assertThat(secondCall).isEqualTo("t-token-test-12345");

    // RestTemplate should only be called once because the token is cached!
    Mockito.verify(restTemplate, Mockito.times(1))
        .exchange(any(String.class), any(HttpMethod.class), any(), eq(LarkBaseTokenProvider.LarkTenantTokenResponse.class));
  }
}
