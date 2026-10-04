package com.company.logicstic.integration.lark.base;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class LarkBaseTokenProvider {

  private static final Logger log = LoggerFactory.getLogger(LarkBaseTokenProvider.class);

  private final LarkProperties properties;
  private final RestTemplate restTemplate;

  private final Object lock = new Object();
  private volatile String cachedToken;
  private volatile long expiresAtEpochMs;

  public LarkBaseTokenProvider(LarkProperties properties, RestTemplate restTemplate) {
    this.properties = properties;
    this.restTemplate = restTemplate;
  }

  public String getTenantAccessToken() {
    long now = System.currentTimeMillis();
    // Re-fetch if token is missing or expiring within 5 minutes (300,000 ms)
    if (cachedToken == null || now >= (expiresAtEpochMs - 300_000L)) {
      synchronized (lock) {
        if (cachedToken == null || System.currentTimeMillis() >= (expiresAtEpochMs - 300_000L)) {
          fetchNewTenantAccessToken();
        }
      }
    }
    return cachedToken;
  }

  public void invalidateCache() {
    synchronized (lock) {
      this.cachedToken = null;
      this.expiresAtEpochMs = 0;
    }
  }

  private void fetchNewTenantAccessToken() {
    if (!StringUtils.hasText(properties.appId()) || !StringUtils.hasText(properties.appSecret())) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "LARK_CONFIG_ERROR",
          "Lark App ID and App Secret must be configured to request tenant_access_token");
    }

    String base = properties.baseUrl().replaceAll("/+$", "");
    String url = base + "/open-apis/auth/v3/tenant_access_token/internal";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    Map<String, String> requestBody = Map.of(
        "app_id", properties.appId().trim(),
        "app_secret", properties.appSecret().trim());

    HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

    try {
      ResponseEntity<LarkTenantTokenResponse> response =
          restTemplate.exchange(url, HttpMethod.POST, requestEntity, LarkTenantTokenResponse.class);
      LarkTenantTokenResponse body = response.getBody();

      if (body == null || body.code() != 0 || !StringUtils.hasText(body.tenantAccessToken())) {
        String msg = body != null ? body.msg() : "empty response";
        log.error("Failed to obtain Lark tenant_access_token: code={}, msg={}", body != null ? body.code() : -1, msg);
        throw new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "LARK_AUTH_FAILED",
            "Failed to obtain Lark tenant_access_token: " + msg);
      }

      this.cachedToken = body.tenantAccessToken();
      long expireSeconds = body.expire() != null && body.expire() > 0 ? body.expire() : 7200L;
      this.expiresAtEpochMs = System.currentTimeMillis() + (expireSeconds * 1000L);

      log.info("Lark tenant_access_token refreshed successfully, expires in {}s", expireSeconds);
    } catch (RestClientException ex) {
      log.error("Network error while requesting Lark tenant_access_token: {}", ex.getMessage());
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "LARK_COMMUNICATION_ERROR",
          "Failed to communicate with Lark token endpoint: " + ex.getMessage());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkTenantTokenResponse(
      int code,
      String msg,
      @JsonProperty("tenant_access_token") String tenantAccessToken,
      Long expire) {}
}
