package com.company.logicstic.modules.identity.service.impl;

import com.company.logicstic.modules.employee.service.CurrentEmployeeLookupService;
import com.company.logicstic.modules.identity.dto.response.CurrentUserResponse;
import com.company.logicstic.modules.identity.service.CurrentUserService;
import com.company.logicstic.shared.exception.ApiException;
import com.company.logicstic.shared.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Profile("!nodb")
@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

  private static final String ROLE_PREFIX = "ROLE_";

  private final CurrentEmployeeLookupService currentEmployeeLookupService;

  @Override
  public CurrentUserResponse getCurrentUser(JwtAuthenticationToken authentication) {
    Jwt jwt = authentication.getToken();
    String email = jwt.getClaimAsString("email");
    UUID employeeId =
        StringUtils.hasText(email)
            ? currentEmployeeLookupService.findEmployeeIdByEmail(email).orElse(null)
            : null;
    List<String> roles =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith(ROLE_PREFIX))
            .map(authority -> authority.substring(ROLE_PREFIX.length()))
            .distinct()
            .sorted()
            .toList();
    return new CurrentUserResponse(
        jwt.getSubject(), email, jwt.getClaimAsString("tenant"), roles, employeeId);
  }

  @Override
  public UUID requireCurrentEmployeeId(JwtAuthenticationToken authentication) {
    UUID employeeId = getCurrentUser(authentication).employeeId();
    if (employeeId == null) {
      throw new ApiException(
          ErrorCode.ACCESS_DENIED, "Authenticated identity is not linked to a tenant employee");
    }
    return employeeId;
  }
}
