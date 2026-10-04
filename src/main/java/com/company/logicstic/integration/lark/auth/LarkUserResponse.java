package com.company.logicstic.integration.lark.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LarkUserResponse(
    @JsonProperty("open_id") String openId,
    @JsonProperty("union_id") String unionId,
    @JsonProperty("user_id") String userId,
    String email,
    @JsonProperty("enterprise_email") String enterpriseEmail,
    String name,
    @JsonProperty("en_name") String enName,
    @JsonProperty("avatar_url") String avatarUrl,
    String mobile) {

  public String effectiveEmail() {
    if (email != null && !email.isBlank()) {
      return email.trim();
    }
    if (enterpriseEmail != null && !enterpriseEmail.isBlank()) {
      return enterpriseEmail.trim();
    }
    return "";
  }

  public String displayName() {
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    if (enName != null && !enName.isBlank()) {
      return enName.trim();
    }
    return effectiveEmail();
  }
}
