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
  private final com.company.logicstic.integration.lark.LarkFailurePolicy failures;

  private final Object lock = new Object();
  private volatile String cachedToken;
  private volatile long expiresAtEpochMs;

  public LarkBaseTokenProvider(LarkProperties properties, RestTemplate restTemplate) {
    this(properties, restTemplate, new com.company.logicstic.integration.lark.LarkFailurePolicy(30));
  }

  @org.springframework.beans.factory.annotation.Autowired
  public LarkBaseTokenProvider(LarkProperties properties, RestTemplate restTemplate, com.company.logicstic.integration.lark.LarkFailurePolicy failures) {
    this.properties = properties;
    this.restTemplate = restTemplate;
    this.failures = failures;
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
        log.error("Lark tenant authentication response rejected providerCode={}", body == null ? null : body.code());
        throw failures.body(body == null ? null : body.code(), com.company.logicstic.integration.lark.LarkFailurePolicy.Credential.APP, response.getHeaders());
      }

      this.cachedToken = body.tenantAccessToken();
      long expireSeconds = body.expire() != null && body.expire() > 0 ? body.expire() : 7200L;
      this.expiresAtEpochMs = System.currentTimeMillis() + (expireSeconds * 1000L);

      log.info("Lark tenant_access_token refreshed successfully, expires in {}s", expireSeconds);
    } catch (RestClientException ex) {
      var failure = failures.transport(ex, com.company.logicstic.integration.lark.LarkFailurePolicy.Credential.APP);
      if (failure.getStatus().is5xxServerError()) log.error("Lark tenant authentication transport failed", com.company.logicstic.exception.ErrorLog.sanitized(ex));
      else log.info("Lark request rejected code={}", failure.getCode());
      throw failure;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkTenantTokenResponse(
      int code,
      String msg,
      @JsonProperty("tenant_access_token") String tenantAccessToken,
      Long expire) {}
}
