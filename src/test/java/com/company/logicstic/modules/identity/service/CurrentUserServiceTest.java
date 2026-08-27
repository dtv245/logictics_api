package com.company.logicstic.modules.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.company.logicstic.modules.employee.service.CurrentEmployeeLookupService;
import com.company.logicstic.modules.identity.dto.response.CurrentUserResponse;
import com.company.logicstic.modules.identity.service.impl.CurrentUserServiceImpl;
import com.company.logicstic.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

  @Mock private CurrentEmployeeLookupService currentEmployeeLookupService;

  private CurrentUserService service;

  @BeforeEach
  void setUp() {
    service = new CurrentUserServiceImpl(currentEmployeeLookupService);
  }

  @Test
  void returnsClaimsSortedRolesAndMappedEmployee() {
    UUID employeeId = UUID.randomUUID();
    given(currentEmployeeLookupService.findEmployeeIdByEmail("driver@example.com"))
        .willReturn(Optional.of(employeeId));

    CurrentUserResponse response = service.getCurrentUser(authentication(true));

    assertThat(response.subject()).isEqualTo("subject-123");
    assertThat(response.email()).isEqualTo("driver@example.com");
    assertThat(response.tenantId()).isEqualTo("tenant-a");
    assertThat(response.roles()).containsExactly("DRIVER", "OWNER");
    assertThat(response.employeeId()).isEqualTo(employeeId);
  }

  @Test
  void returnsNullEmployeeIdWhenIdentityHasNoEmployee() {
    given(currentEmployeeLookupService.findEmployeeIdByEmail("driver@example.com"))
        .willReturn(Optional.empty());

    assertThat(service.getCurrentUser(authentication(true)).employeeId()).isNull();
  }

  @Test
  void missingEmailDoesNotAttemptEmployeeLookup() {
    CurrentUserResponse response = service.getCurrentUser(authentication(false));

    assertThat(response.email()).isNull();
    assertThat(response.employeeId()).isNull();
    verifyNoInteractions(currentEmployeeLookupService);
  }

  @Test
  void requiresMappedEmployeeForEmployeeBackedWorkflow() {
    UUID employeeId = UUID.randomUUID();
    given(currentEmployeeLookupService.findEmployeeIdByEmail("driver@example.com"))
        .willReturn(Optional.of(employeeId));

    assertThat(service.requireCurrentEmployeeId(authentication(true))).isEqualTo(employeeId);
  }

  @Test
  void rejectsEmployeeBackedWorkflowWhenIdentityIsUnmapped() {
    given(currentEmployeeLookupService.findEmployeeIdByEmail("driver@example.com"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.requireCurrentEmployeeId(authentication(true)))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getStatus().value()).isEqualTo(403);
              assertThat(exception.getCode()).isEqualTo("ACCESS_DENIED");
            });
  }

  private static JwtAuthenticationToken authentication(boolean includeEmail) {
    Instant now = Instant.now();
    Jwt.Builder jwtBuilder =
        Jwt.withTokenValue("token-value")
            .header("alg", "none")
            .subject("subject-123")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("tenant", "tenant-a");
    if (includeEmail) {
      jwtBuilder.claim("email", "driver@example.com");
    }
    Jwt jwt = jwtBuilder.build();
    return new JwtAuthenticationToken(
        jwt,
        List.of(
            new SimpleGrantedAuthority("ROLE_OWNER"),
            new SimpleGrantedAuthority("SCOPE_read"),
            new SimpleGrantedAuthority("ROLE_DRIVER"),
            new SimpleGrantedAuthority("ROLE_DRIVER")));
  }
}
