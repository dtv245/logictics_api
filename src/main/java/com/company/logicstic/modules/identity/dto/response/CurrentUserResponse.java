package com.company.logicstic.modules.identity.dto.response;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
    String subject, String email, String tenantId, List<String> roles, UUID employeeId) {

  public CurrentUserResponse {
    roles = roles == null ? List.of() : List.copyOf(roles);
  }
}
