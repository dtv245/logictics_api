package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
public class LarkAuthClient {

  private static final Logger log = LoggerFactory.getLogger(LarkAuthClient.class);

  private final LarkProperties properties;
  private final RestTemplate restTemplate;

  public LarkAuthClient(LarkProperties properties, RestTemplate restTemplate) {
    this.properties = properties;
    this.restTemplate = restTemplate;
  }

  private final Object tokenLock = new Object();
  private volatile String cachedAppAccessToken;
  private volatile long appTokenExpiresAtEpochMs;

  public String buildAuthorizeUrl(String state, String redirectUri) {
    String resolvedRedirectUri =
        StringUtils.hasText(redirectUri) ? redirectUri : properties.redirectUri();
    String encodedRedirectUri = URLEncoder.encode(resolvedRedirectUri, StandardCharsets.UTF_8);
    String encodedState =
        StringUtils.hasText(state) ? URLEncoder.encode(state, StandardCharsets.UTF_8) : "";
    String base = properties.baseUrl().replaceAll("/+$", "");

    return base
        + "/open-apis/authen/v1/authorize?app_id="
        + properties.appId()
        + "&redirect_uri="
        + encodedRedirectUri
        + "&response_type=code"
        + (StringUtils.hasText(encodedState) ? "&state=" + encodedState : "");
  }

  public String getAppAccessToken() {
    long now = System.currentTimeMillis();
    // Re-fetch if token is missing or expiring within 5 minutes (300,000 ms)
    if (cachedAppAccessToken == null || now >= (appTokenExpiresAtEpochMs - 300_000L)) {
      synchronized (tokenLock) {
        if (cachedAppAccessToken == null || System.currentTimeMillis() >= (appTokenExpiresAtEpochMs - 300_000L)) {
          fetchNewAppAccessToken();
        }
      }
    }
    return cachedAppAccessToken;
  }

  public void invalidateAppAccessTokenCache() {
    synchronized (tokenLock) {
      this.cachedAppAccessToken = null;
      this.appTokenExpiresAtEpochMs = 0;
    }
  }

  private void fetchNewAppAccessToken() {
    if (!StringUtils.hasText(properties.appId()) || !StringUtils.hasText(properties.appSecret())) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "LARK_CONFIG_ERROR",
          "Lark App ID and App Secret must be configured to request app_access_token");
    }

    String base = properties.baseUrl().replaceAll("/+$", "");
    String url = base + "/open-apis/auth/v3/app_access_token/internal";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    Map<String, String> requestBody =
        Map.of(
            "app_id", properties.appId().trim(),
            "app_secret", properties.appSecret().trim());

    HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

    try {
      ResponseEntity<LarkAppTokenResponse> response =
          restTemplate.exchange(url, HttpMethod.POST, requestEntity, LarkAppTokenResponse.class);
      LarkAppTokenResponse body = response.getBody();

      if (body == null || body.code() != 0 || !StringUtils.hasText(body.appAccessToken())) {
        String msg = body != null ? body.msg() : "empty response";
        log.error("Failed to obtain Lark app_access_token: code={}, msg={}", body != null ? body.code() : -1, msg);
        throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            "Failed to obtain Lark app_access_token: " + msg);
      }

      this.cachedAppAccessToken = body.appAccessToken();
      long expireSeconds = body.expire() != null && body.expire() > 0 ? body.expire() : 7200L;
      this.appTokenExpiresAtEpochMs = System.currentTimeMillis() + (expireSeconds * 1000L);

      log.info("Lark app_access_token refreshed successfully, expires in {}s", expireSeconds);
    } catch (RestClientException ex) {
      log.error("Network error while requesting Lark app_access_token: {}", ex.getMessage());
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "UNAUTHORIZED",
          "Failed to communicate with Lark app token endpoint: " + ex.getMessage());
    }
  }

  public LarkTokenResponse exchangeCodeForToken(String code, String redirectUri) {
    if (!StringUtils.hasText(code)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Authorization code must not be blank");
    }

    return executeExchangeCodeForToken(code, true);
  }

  private LarkTokenResponse executeExchangeCodeForToken(String code, boolean retryOnInvalidToken) {
    String appAccessToken = getAppAccessToken();

    String base = properties.baseUrl().replaceAll("/+$", "");
    String tokenUrl = base + "/open-apis/authen/v1/access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(appAccessToken);

    Map<String, String> requestPayload =
        Map.of(
            "grant_type", "authorization_code",
            "code", code.trim());

    HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestPayload, headers);

    try {
      ResponseEntity<LarkTokenResponse> response =
          restTemplate.exchange(tokenUrl, HttpMethod.POST, requestEntity, LarkTokenResponse.class);
      LarkTokenResponse tokenResponse = response.getBody();

      if (tokenResponse == null) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Empty response from Lark token endpoint");
      }

      // Lark code 20014: App access token verification failed. Invalidate cache and retry once.
      if (tokenResponse.code() == 20014 && retryOnInvalidToken) {
        log.warn("Lark token endpoint returned code 20014; invalidating cached app_access_token and retrying once");
        invalidateAppAccessTokenCache();
        return executeExchangeCodeForToken(code, false);
      }

      if (tokenResponse.code() != 0) {
        log.warn("Lark token endpoint returned code {}: {}", tokenResponse.code(), tokenResponse.msg());
        throw new ApiException(
            HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Lark authentication failed: " + tokenResponse.msg());
      }

      return tokenResponse;
    } catch (RestClientException ex) {
      log.error("Failed to communicate with Lark token endpoint: {}", ex.getMessage());
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Failed to communicate with Lark server: " + ex.getMessage());
    }
  }

  public LarkUserResponse extractUser(LarkTokenResponse tokenResponse) {
    if (tokenResponse == null || tokenResponse.data() == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token response does not contain user data");
    }

    LarkTokenResponse.LarkTokenData data = tokenResponse.data();
    String email = StringUtils.hasText(data.email()) ? data.email() : data.enterpriseEmail();

    if (!StringUtils.hasText(email) && StringUtils.hasText(data.accessToken())) {
      LarkUserResponse fetchedUser = fetchUserInfo(data.accessToken(), data);
      if (fetchedUser != null) {
        return fetchedUser;
      }
    }

    return new LarkUserResponse(
        data.openId(),
        data.unionId(),
        data.userId(),
        data.email(),
        data.enterpriseEmail(),
        data.name(),
        data.enName(),
        data.avatarUrl(),
        data.mobile());
  }

  private LarkUserResponse fetchUserInfo(String userAccessToken, LarkTokenResponse.LarkTokenData fallback) {
    try {
      String base = properties.baseUrl().replaceAll("/+$", "");
      String userInfoUrl = base + "/open-apis/authen/v1/user_info";

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(userAccessToken);
      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<LarkUserInfoWrapper> response =
          restTemplate.exchange(userInfoUrl, HttpMethod.GET, entity, LarkUserInfoWrapper.class);
      LarkUserInfoWrapper body = response.getBody();

      if (body != null && body.code() == 0 && body.data() != null) {
        LarkUserResponse info = body.data();
        return new LarkUserResponse(
            StringUtils.hasText(info.openId()) ? info.openId() : fallback.openId(),
            StringUtils.hasText(info.unionId()) ? info.unionId() : fallback.unionId(),
            StringUtils.hasText(info.userId()) ? info.userId() : fallback.userId(),
            info.email(),
            info.enterpriseEmail(),
            StringUtils.hasText(info.name()) ? info.name() : fallback.name(),
            StringUtils.hasText(info.enName()) ? info.enName() : fallback.enName(),
            StringUtils.hasText(info.avatarUrl()) ? info.avatarUrl() : fallback.avatarUrl(),
            StringUtils.hasText(info.mobile()) ? info.mobile() : fallback.mobile());
      }
    } catch (RestClientException ex) {
      log.debug("Lark user_info fetch non-fatal failure: {}", ex.getMessage());
    }
    return null;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkUserInfoWrapper(int code, String msg, LarkUserResponse data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LarkAppTokenResponse(
      int code,
      String msg,
      @JsonProperty("app_access_token") String appAccessToken,
      Long expire) {}
}
