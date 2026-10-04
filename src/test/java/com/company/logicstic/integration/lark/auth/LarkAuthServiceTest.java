package com.company.logicstic.integration.lark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LarkAuthServiceTest {

  @Mock private LarkAuthClient authClient;
  @Mock private EmployeeRepository employeeRepository;

  private LarkProperties properties;
  private LarkAuthService authService;

  @BeforeEach
  void setUp() {
    properties =
        new LarkProperties(
            true,
            "cli_test_123",
            "sec_test_secret",
            "http://localhost:5173/auth/callback",
            "https://open.larksuite.com",
            "my-super-secret-jwt-key-with-more-than-256-bits-length!",
            "https://open.larksuite.com",
            Duration.ofHours(8),
            Duration.ofMinutes(15),
            "test-tenant");

    authService = new LarkAuthService(properties, authClient, employeeRepository);
  }

  @Test
  @DisplayName("createState and validateState should succeed with matching returnTo")
  void stateValidation_success() {
    String returnTo = "/dashboard/loads";
    String state = authService.createState(returnTo);

    assertThat(state).isNotBlank();
    String validatedReturnTo = authService.validateState(state);
    assertThat(validatedReturnTo).isEqualTo(returnTo);
  }

  @Test
  @DisplayName("validateState with invalid state should throw BadRequest")
  void stateValidation_tampered_throwsBadRequest() {
    assertThatThrownBy(() -> authService.validateState("invalid_tampered_state"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("handleCallback succeeds when employee is active and mapped")
  void handleCallback_success() {
    String state = authService.createState("/operations");
    String email = "dispatcher@company.com";

    LarkTokenResponse.LarkTokenData tokenData =
        new LarkTokenResponse.LarkTokenData(
            "u-mock-access-token",
            "Bearer",
            7200L,
            "ur-mock-refresh",
            2592000L,
            "ou_lark_123",
            "on_lark_123",
            "usr_123",
            "John Doe",
            "John",
            "https://avatar.com/john.jpg",
            email,
            null,
            "+123456789");
    LarkTokenResponse tokenResponse = new LarkTokenResponse(0, "success", tokenData);

    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(
            new LarkUserResponse(
                "ou_lark_123",
                "on_lark_123",
                "usr_123",
                email,
                null,
                "John Doe",
                "John",
                "https://avatar.com/john.jpg",
                "+123456789"));

    Employee employee = new Employee();
    UUID employeeId = UUID.randomUUID();
    employee.setId(employeeId);
    employee.setEmail(email);
    employee.setFirstName("John");
    employee.setLastName("Doe");
    employee.setStatus("ACTIVE");

    TenantRole role = new TenantRole();
    role.setName("DISPATCHER");
    employee.setRole(role);

    given(employeeRepository.findByEmail(email)).willReturn(Optional.of(employee));

    LarkAuthService.LarkLoginResult result =
        authService.handleCallback("auth_code_123", state, null, null, "/operations");

    assertThat(result).isNotNull();
    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.email()).isEqualTo(email);
    assertThat(result.roles()).contains("DISPATCHER");
    assertThat(result.tenantId()).isEqualTo("test-tenant");
    assertThat(result.returnTo()).isEqualTo("/operations");

    Claims claims = authService.validateInternalToken(result.accessToken());
    assertThat(claims.getSubject()).isEqualTo("ou_lark_123");
    assertThat(claims.get("email")).isEqualTo(email);
    assertThat(claims.get("tenant")).isEqualTo("test-tenant");
  }

  @Test
  @DisplayName("handleCallback throws Forbidden if employee is not found")
  void handleCallback_unregisteredEmployee_throwsForbidden() {
    String state = authService.createState("/dashboard");
    String email = "stranger@external.com";

    LarkTokenResponse.LarkTokenData tokenData =
        new LarkTokenResponse.LarkTokenData(
            "u-mock", "Bearer", 7200L, null, null, "ou_unknown", null, null, "Stranger", null, null, email, null, null);
    LarkTokenResponse tokenResponse = new LarkTokenResponse(0, "success", tokenData);

    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse("ou_unknown", null, null, email, null, "Stranger", null, null, null));

    given(employeeRepository.findByEmail(email)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.handleCallback("auth_code_123", state, null, null, "/dashboard"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("chưa được liên kết");
  }
}
