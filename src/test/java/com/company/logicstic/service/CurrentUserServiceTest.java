package com.company.logicstic.service;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.EmployeeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserServiceTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final CurrentUserService service = new CurrentUserService(employees);

    @AfterEach void clear() { TenantContext.clear(); }

    private JwtAuthenticationToken principal(String subject, String email, String tenant) {
        var jwt = Jwt.withTokenValue("test-only").header("typ", "JWT").subject(subject).claim("tenant", tenant);
        if (email != null) jwt.claim("email", email);
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority("ROLE_ACCOUNTANT"),
                new SimpleGrantedAuthority("ROLE_PAYROLL_MANAGER"), new SimpleGrantedAuthority("SCOPE_openid")));
    }

    @Test void preservesJwtSubjectAndEmailAndExactAuthoritiesWithTenantLocalEmployee() {
        TenantContext.setTenantId("tenant-a");
        var employee = new Employee(); employee.setId(UUID.randomUUID());
        when(employees.findByEmail("person@example.test")).thenReturn(Optional.of(employee));
        var response = service.current(principal("oidc-subject", "person@example.test", "tenant-a"));
        assertEquals("oidc-subject", response.subject()); assertEquals("person@example.test", response.email());
        assertEquals("tenant-a", response.tenantId()); assertEquals(employee.getId(), response.employeeId());
        assertEquals(List.of("ACCOUNTANT", "PAYROLL_MANAGER"), response.roles());
        assertFalse(response.roles().contains("MANAGER")); assertFalse(response.roles().contains("OWNER"));
    }

    @Test void noEmployeeMappingReturnsNullWithoutTrustingEmployeeIdClaim() {
        TenantContext.setTenantId("tenant-a");
        when(employees.findByEmail("unknown@example.test")).thenReturn(Optional.empty());
        assertNull(service.current(principal("unmapped", "unknown@example.test", "tenant-a")).employeeId());
    }

    @Test void emailMayBeNullWithoutFabricatingAnEmployee() {
        TenantContext.setTenantId("tenant-a");
        var response = service.current(principal("no-email", null, "tenant-a"));
        assertNull(response.email()); assertNull(response.employeeId()); verifyNoInteractions(employees);
    }

    @Test void unauthenticatedAndAnonymousRequestsAre401BeforeAnyLookup() {
        assertEquals(401, assertThrows(ApiException.class, () -> service.current(null)).getStatus().value());
        var anonymous = new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertEquals(401, assertThrows(ApiException.class, () -> service.current(anonymous)).getStatus().value());
        assertEquals(401, assertThrows(ApiException.class, () -> service.current(
                new UsernamePasswordAuthenticationToken("user", "password"))).getStatus().value());
        verifyNoInteractions(employees);
    }

    @Test void unresolvedAndMismatchedTenantsFailClosedBeforeLookingUpEmployees() {
        var principal = principal("subject", "person@example.test", "tenant-a");
        assertEquals("IDENTITY_TENANT_UNRESOLVED", assertThrows(ApiException.class, () -> service.current(principal)).getCode());
        TenantContext.setTenantId("tenant-b");
        assertEquals("IDENTITY_TENANT_MISMATCH", assertThrows(ApiException.class, () -> service.current(principal)).getCode());
        verifyNoInteractions(employees);
    }

    @Test void ambiguousEmployeeMappingsReturnNormalizedConflict() {
        TenantContext.setTenantId("tenant-a");
        when(employees.findByEmail("duplicate@example.test")).thenThrow(new IncorrectResultSizeDataAccessException(1, 2));
        assertEquals("IDENTITY_EMPLOYEE_MAPPING_AMBIGUOUS", assertThrows(ApiException.class,
                () -> service.current(principal("subject", "duplicate@example.test", "tenant-a"))).getCode());
    }

    @Test void existingAuthenticatedDevelopmentPrincipalUsesBoundTenantAndEmailName() {
        TenantContext.setTenantId("explicit-test-tenant");
        var response = service.current(new UsernamePasswordAuthenticationToken("test@example.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_DRIVER"))));
        assertEquals("test@example.test", response.subject()); assertEquals("explicit-test-tenant", response.tenantId());
        assertEquals(List.of("DRIVER"), response.roles());
    }
}
