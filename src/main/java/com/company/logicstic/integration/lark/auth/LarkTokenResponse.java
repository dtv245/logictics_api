package com.company.logicstic.integration.lark.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LarkTokenResponse(
    int code,
    String msg,
    LarkTokenData data) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record LarkTokenData(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("refresh_expires_in") Long refreshExpiresIn,
      @JsonProperty("open_id") String openId,
      @JsonProperty("union_id") String unionId,
      @JsonProperty("user_id") String userId,
      String name,
      @JsonProperty("en_name") String enName,
      @JsonProperty("avatar_url") String avatarUrl,
      String email,
      @JsonProperty("enterprise_email") String enterpriseEmail,
      String mobile) {}
}
