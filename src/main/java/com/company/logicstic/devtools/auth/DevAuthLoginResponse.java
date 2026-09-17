package com.company.logicstic.devtools.auth;

import java.util.List;

public record DevAuthLoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String subject,
    String email,
    String tenantId,
    List<String> roles) {

  public DevAuthLoginResponse {
    roles = List.copyOf(roles);
  }
}
