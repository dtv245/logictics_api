package com.company.logicstic.modules.identity.service;

import com.company.logicstic.modules.identity.dto.response.CurrentUserResponse;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public interface CurrentUserService {

  CurrentUserResponse getCurrentUser(JwtAuthenticationToken authentication);

  /**
   * Returns the tenant-local Employee linked to the authenticated identity.
   *
   * @throws com.company.logicstic.shared.exception.ApiException with {@code ACCESS_DENIED} when the
   *     identity is valid but has no Employee mapping
   */
  UUID requireCurrentEmployeeId(JwtAuthenticationToken authentication);
}
