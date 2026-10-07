package com.company.logicstic.service;

import com.company.logicstic.config.TenantContext;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.EmployeeRepository;
import java.util.List;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {
    private final EmployeeRepository employees;

    public CurrentUserService(EmployeeRepository employees) {
        this.employees = employees;
    }

    public CurrentUserResponse current() {
        return current(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
    }

    public CurrentUserResponse requireMappedEmployee() {
        CurrentUserResponse identity = current();
        if (identity.employeeId() == null)
            throw new com.company.logicstic.exception.ForbiddenException("Authenticated user has no employee profile in this tenant");
        return identity;
    }

    public CurrentUserResponse current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required");
        }
        String tenant = TenantContext.getTenantId().orElseThrow(() -> new ApiException(
                HttpStatus.FORBIDDEN, "IDENTITY_TENANT_UNRESOLVED", "Authenticated tenant is not resolved"));
        String subject;
        String email;
        if (authentication instanceof JwtAuthenticationToken jwt) {
            subject = jwt.getToken().getSubject();
            email = jwt.getToken().getClaimAsString("email");
            if (!tenant.equals(jwt.getToken().getClaimAsString("tenant"))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "IDENTITY_TENANT_MISMATCH", "Authenticated tenant boundary does not match");
            }
        } else {
            // Existing authenticated test/development principals use the same
            // email-name employee convention as current controller actor lookups.
            subject = authentication.getName();
            email = authentication.getName();
        }
        if (!StringUtils.hasText(subject)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "IDENTITY_SUBJECT_MISSING", "Authenticated subject is required");
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .distinct().sorted().toList();
        try {
            var employeeId = StringUtils.hasText(email)
                    ? employees.findByEmail(email).map(Employee::getId).orElse(null) : null;
            return new CurrentUserResponse(subject, email, tenant, roles, employeeId);
        } catch (IncorrectResultSizeDataAccessException ambiguous) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTITY_EMPLOYEE_MAPPING_AMBIGUOUS", "Authenticated employee mapping is ambiguous");
        }
    }
}
