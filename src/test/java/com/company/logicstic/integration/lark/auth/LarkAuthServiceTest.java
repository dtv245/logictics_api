package com.company.logicstic.integration.lark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.LarkUserMapping;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LarkUserMappingRepository;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LarkAuthServiceTest {

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Mock private LarkAuthClient authClient;
  @Mock private EmployeeRepository employeeRepository;
  @Mock private LarkUserMappingRepository larkUserMappingRepository;

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

    authService =
        new LarkAuthService(properties, authClient, employeeRepository, larkUserMappingRepository, null);
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
  @DisplayName("Existing mapping -> login success without querying employee by email")
  void existingMapping_loginSuccess() {
    String state = authService.createState("/dashboard");
    String openId = "ou_existing_user";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, "unmatched@lark.com");
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, "on_123", "usr_123", "unmatched@lark.com", null, "Existing User", null, null, null));

    Employee employee = createEmployee("linked.employee@company.com", "ACTIVE", "DISPATCHER");
    LarkUserMapping existingMapping = new LarkUserMapping();
    existingMapping.setOpenId(openId);
    existingMapping.setEmployee(employee);
    existingMapping.setEmail("linked.employee@company.com");

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.of(existingMapping));

    LarkAuthService.LarkLoginResult result =
        authService.handleCallback("auth_code_123", state, null, null, "/dashboard");

    assertThat(result).isNotNull();
    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.email()).isEqualTo("linked.employee@company.com");
    assertThat(result.roles()).contains("DISPATCHER");
    assertThat(result.employeeId()).isEqualTo(employee.getId());

    verify(employeeRepository, never()).findAllByEmailForLarkLogin(anyString());
    verify(employeeRepository, never()).findByEmailForLarkLogin(anyString());
  }

  @Test
  @DisplayName("Existing mapping takes precedence over email lookup even if Lark email differs")
  void existingMapping_precedenceOverEmail() {
    String state = authService.createState("/dashboard");
    String openId = "ou_precedence_user";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, "changed_lark_email@domain.com");
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, "on_123", "usr_123", "changed_lark_email@domain.com", null, "Name", null, null, null));

    Employee mappedEmployee = createEmployee("original@company.com", "ACTIVE", "ADMIN");
    LarkUserMapping mapping = new LarkUserMapping();
    mapping.setOpenId(openId);
    mapping.setEmployee(mappedEmployee);
    mapping.setEmail("original@company.com");

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.of(mapping));

    LarkAuthService.LarkLoginResult result =
        authService.handleCallback("code_123", state, null, null, null);

    assertThat(result.email()).isEqualTo("original@company.com");
    assertThat(result.roles()).contains("ADMIN");
    verify(employeeRepository, never()).findAllByEmailForLarkLogin(anyString());
  }

  @Test
  @DisplayName("No mapping + email matches single active employee -> auto-link and login success")
  void noMapping_singleActiveEmployee_autoLinksAndSucceeds() {
    String state = authService.createState("/operations");
    String email = "dispatcher@company.com";
    String openId = "ou_new_user";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, email);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, "on_new", "usr_new", email, null, "John Doe", "John", "https://avatar.com/john.jpg", "+123456789"));

    Employee employee = createEmployee(email, "ACTIVE", "DISPATCHER");

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.empty());
    given(larkUserMappingRepository.findByUnionIdWithEmployee(anyString())).willReturn(Optional.empty());
    given(employeeRepository.findAllByEmailForLarkLogin(email)).willReturn(List.of(employee));

    LarkAuthService.LarkLoginResult result =
        authService.handleCallback("auth_code_123", state, null, null, "/operations");

    assertThat(result).isNotNull();
    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.email()).isEqualTo(email);
    assertThat(result.roles()).contains("DISPATCHER");
    assertThat(result.tenantId()).isEqualTo("test-tenant");
    assertThat(result.returnTo()).isEqualTo("/operations");

    verify(larkUserMappingRepository).saveAndFlush(argThat(m ->
        m.getEmployee().getId().equals(employee.getId())
            && openId.equals(m.getOpenId())
            && email.equals(m.getEmail())));
  }

  @Test
  @DisplayName("No mapping + email does not match any employee -> 403 LARK_EMPLOYEE_NOT_LINKED")
  void noMapping_emailNotMatched_throws403() {
    String state = authService.createState("/dashboard");
    String email = "unknown@external.com";
    String openId = "ou_unknown";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, email);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, email, null, "Stranger", null, null, null));

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.empty());
    given(employeeRepository.findAllByEmailForLarkLogin(email)).willReturn(Collections.emptyList());

    assertThatThrownBy(() -> authService.handleCallback("auth_code_123", state, null, null, "/dashboard"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(apiEx.getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
          assertThat(apiEx.getMessage()).isEqualTo("No active employee is linked to this Lark account");
        });
  }

  @Test
  @DisplayName("No mapping + duplicate employee email -> no auto-link, 403 LARK_EMPLOYEE_NOT_LINKED")
  void noMapping_duplicateEmployeeEmail_throws403() {
    String state = authService.createState("/dashboard");
    String email = "duplicate@company.com";
    String openId = "ou_dup";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, email);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, email, null, "Duplicate User", null, null, null));

    Employee emp1 = createEmployee(email, "ACTIVE", "DISPATCHER");
    Employee emp2 = createEmployee(email, "ACTIVE", "ACCOUNTANT");

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.empty());
    given(employeeRepository.findAllByEmailForLarkLogin(email)).willReturn(List.of(emp1, emp2));

    assertThatThrownBy(() -> authService.handleCallback("code", state, null, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(apiEx.getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
        });

    verify(larkUserMappingRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("No mapping + inactive employee -> no auto-link, 403 LARK_EMPLOYEE_NOT_LINKED")
  void noMapping_inactiveEmployee_throws403() {
    String state = authService.createState("/dashboard");
    String email = "inactive@company.com";
    String openId = "ou_inactive";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, email);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, email, null, "Inactive User", null, null, null));

    Employee inactiveEmployee = createEmployee(email, "SUSPENDED", "DISPATCHER");

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.empty());
    given(employeeRepository.findAllByEmailForLarkLogin(email)).willReturn(List.of(inactiveEmployee));

    assertThatThrownBy(() -> authService.handleCallback("code", state, null, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(apiEx.getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
        });

    verify(larkUserMappingRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Existing mapping + inactive mapped employee -> 403 LARK_EMPLOYEE_NOT_LINKED")
  void existingMapping_inactiveEmployee_throws403() {
    String state = authService.createState("/dashboard");
    String openId = "ou_mapped_inactive";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, "any@lark.com");
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, "any@lark.com", null, "User", null, null, null));

    Employee employee = createEmployee("emp@company.com", "TERMINATED", "DISPATCHER");
    LarkUserMapping mapping = new LarkUserMapping();
    mapping.setOpenId(openId);
    mapping.setEmployee(employee);

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.of(mapping));

    assertThatThrownBy(() -> authService.handleCallback("code", state, null, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(apiEx.getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
        });
  }

  @Test
  @DisplayName("No mapping + null/blank email -> no auto-link, 403 LARK_EMPLOYEE_NOT_LINKED")
  void noMapping_nullOrBlankEmail_throws403() {
    String state = authService.createState("/dashboard");
    String openId = "ou_no_email";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, null);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, null, "   ", "No Email", null, null, null));

    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.handleCallback("code", state, null, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(apiEx.getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
        });

    verify(employeeRepository, never()).findAllByEmailForLarkLogin(anyString());
  }

  @Test
  @DisplayName("Concurrent auto-link insert -> catches DataIntegrityViolationException and reloads mapping")
  void concurrentAutoLink_reloadsMappingAndSucceeds() {
    String state = authService.createState("/dashboard");
    String email = "race@company.com";
    String openId = "ou_race";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, email);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, email, null, "Race User", null, null, null));

    Employee employee = createEmployee(email, "ACTIVE", "DISPATCHER");

    LarkUserMapping insertedByConcurrent = new LarkUserMapping();
    insertedByConcurrent.setOpenId(openId);
    insertedByConcurrent.setEmployee(employee);
    insertedByConcurrent.setEmail(email);

    // Initial lookup returns empty; save throws constraint violation; second lookup returns saved mapping
    given(larkUserMappingRepository.findByOpenIdWithEmployee(openId))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(insertedByConcurrent));
    given(employeeRepository.findAllByEmailForLarkLogin(email)).willReturn(List.of(employee));
    given(larkUserMappingRepository.saveAndFlush(any(LarkUserMapping.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

    LarkAuthService.LarkLoginResult result =
        authService.handleCallback("code", state, null, null, "/dashboard");

    assertThat(result).isNotNull();
    assertThat(result.email()).isEqualTo(email);
    assertThat(result.employeeId()).isEqualTo(employee.getId());
  }

  @Test
  @DisplayName("Login without persistent mapping repository fails closed")
  void loginWithoutMappingRepository_throws403() {
    LarkAuthService minimalService =
        new LarkAuthService(properties, authClient, employeeRepository);

    String state = minimalService.createState("/minimal");
    String email = "minimal@company.com";
    String openId = "ou_minimal";

    LarkTokenResponse tokenResponse = createTokenResponse(openId, email);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(tokenResponse);
    given(authClient.extractUser(tokenResponse))
        .willReturn(new LarkUserResponse(openId, null, null, email, null, "Minimal", null, null, null));

    assertThatThrownBy(() -> minimalService.handleCallback("code", state, null, null, "/minimal"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(((ApiException) ex).getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
        });
    verifyNoInteractions(employeeRepository);
  }

  @Test
  void callbackCannotTransferAnEmployeeRoleToAnotherTokenTenant() {
    String email = "admin@fixture.test";
    Employee employee = createEmployee(email, "ACTIVE", "ADMIN");
    var response = new LarkTokenResponse(0, "success", null);

    lenient().when(authClient.exchangeCodeForToken(anyString(), anyString())).thenReturn(response);
    lenient().when(authClient.extractUser(response)).thenReturn(
        new LarkUserResponse("fixture-subject", null, null, email, null, "Fixture", null, null, null));
    lenient().when(employeeRepository.findByEmailForLarkLogin(email)).thenReturn(Optional.of(employee));
    lenient().when(employeeRepository.findAllByEmailForLarkLogin(email)).thenReturn(List.of(employee));

    String state = authService.createState("/");
    TenantContext.setTenantId("another-physical-tenant");
    assertThatThrownBy(() -> authService.handleCallback("fixture-code", state, null, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("IDENTITY_TENANT_MISMATCH"));
    verifyNoInteractions(authClient, employeeRepository, larkUserMappingRepository);
    assertThat(TenantContext.getTenantId()).contains("another-physical-tenant");
  }

  @Test
  void unionMappingIsUsedWithoutEmailAndAfterMissingOpenMapping() {
    var user = new LarkUserResponse(" new-open ", " existing-union ", null,
        null, null, "Mapped", null, null, null);
    stubUser(user);
    Employee employee = createEmployee("mapped@company.test", "ACTIVE", "DRIVER");
    var mapping = new LarkUserMapping();
    mapping.setEmployee(employee);
    given(larkUserMappingRepository.findByUnionIdWithEmployee("existing-union"))
        .willReturn(Optional.of(mapping));

    var result = authService.handleCallback("code", authService.createState("/"), null, null, null);

    assertThat(result.employeeId()).isEqualTo(employee.getId());
    verify(larkUserMappingRepository).findByOpenIdWithEmployee("new-open");
    verifyNoInteractions(employeeRepository);
    verify(larkUserMappingRepository, never()).saveAndFlush(any());
  }

  @Test
  void openMappingWinsBeforeUnionMapping() {
    stubUser(new LarkUserResponse("open", "union", null, null, null, "Mapped", null, null, null));
    Employee employee = createEmployee("mapped@company.test", "ACTIVE", "DRIVER");
    var mapping = new LarkUserMapping();
    mapping.setEmployee(employee);
    given(larkUserMappingRepository.findByOpenIdWithEmployee("open")).willReturn(Optional.of(mapping));

    assertThat(authService.handleCallback("code", authService.createState("/"), null, null, null)
        .employeeId()).isEqualTo(employee.getId());
    verify(larkUserMappingRepository, never()).findByUnionIdWithEmployee(anyString());
    verifyNoInteractions(employeeRepository);
  }

  @Test
  void emailAndIdentifiersAreNormalizedBeforePersistingAndOnlyListLookupIsUsed() {
    stubUser(new LarkUserResponse(" open ", " union ", " user ", "  Mixed@Company.TEST  ",
        null, "New", null, null, null));
    Employee employee = createEmployee("mixed@company.test", "ACTIVE", "DISPATCHER");
    given(employeeRepository.findAllByEmailForLarkLogin("mixed@company.test")).willReturn(List.of(employee));

    authService.handleCallback("code", authService.createState("/"), null, null, null);

    verify(larkUserMappingRepository).saveAndFlush(argThat(mapping ->
        "open".equals(mapping.getOpenId()) && "union".equals(mapping.getUnionId())
            && "user".equals(mapping.getLarkUserId()) && "mixed@company.test".equals(mapping.getEmail())));
    verify(employeeRepository, never()).findByEmailForLarkLogin(anyString());
    verify(employeeRepository, never()).save(any());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\r\n"})
  void absentEmailIsForbidden(String email) {
    stubUser(new LarkUserResponse("open", null, null, email, null, "No email", null, null, null));
    assertNotLinked();
    verifyNoInteractions(employeeRepository);
    verify(larkUserMappingRepository, never()).saveAndFlush(any());
  }

  @Test
  void missingStableIdentifiersCannotCreateUnusableMapping() {
    stubUser(new LarkUserResponse(null, " ", "user-only", "employee@company.test",
        null, "No stable id", null, null, null));
    given(employeeRepository.findAllByEmailForLarkLogin("employee@company.test"))
        .willReturn(List.of(createEmployee("employee@company.test", "ACTIVE", "DRIVER")));
    assertNotLinked();
    verify(larkUserMappingRepository, never()).saveAndFlush(any());
  }

  @Test
  void concurrentConflictWithoutActiveWinnerIsForbidden() {
    stubUser(new LarkUserResponse("race", null, null, "race@company.test", null, "Race", null, null, null));
    given(employeeRepository.findAllByEmailForLarkLogin("race@company.test"))
        .willReturn(List.of(createEmployee("race@company.test", "ACTIVE", "DRIVER")));
    given(larkUserMappingRepository.saveAndFlush(any()))
        .willThrow(new DataIntegrityViolationException("constraint violation"));
    assertNotLinked();
  }

  private void assertNotLinked() {
    String state = authService.createState("/");
    assertThatThrownBy(() -> authService.handleCallback("code", state, null, null, null))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(((ApiException) ex).getCode()).isEqualTo("LARK_EMPLOYEE_NOT_LINKED");
        });
  }

  private void stubUser(LarkUserResponse user) {
    var token = new LarkTokenResponse(0, "success", null);
    given(authClient.exchangeCodeForToken(anyString(), anyString())).willReturn(token);
    given(authClient.extractUser(token)).willReturn(user);
  }

  private Employee createEmployee(String email, String status, String roleName) {
    Employee employee = new Employee();
    employee.setId(UUID.randomUUID());
    employee.setEmail(email);
    employee.setFirstName("First");
    employee.setLastName("Last");
    employee.setStatus(status);

    if (roleName != null) {
      TenantRole role = new TenantRole();
      role.setName(roleName);
      employee.setRole(role);
    }
    return employee;
  }

  private LarkTokenResponse createTokenResponse(String openId, String email) {
    LarkTokenResponse.LarkTokenData tokenData =
        new LarkTokenResponse.LarkTokenData(
            "u-mock-access-token",
            "Bearer",
            7200L,
            "ur-mock-refresh",
            2592000L,
            openId,
            "on_" + openId,
            "usr_123",
            "Mock User",
            "Mock",
            "https://avatar.com/mock.jpg",
            email,
            null,
            "+123456789");
    return new LarkTokenResponse(0, "success", tokenData);
  }
}
