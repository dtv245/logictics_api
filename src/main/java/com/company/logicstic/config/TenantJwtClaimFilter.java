package com.company.logicstic.config;

import java.io.IOException;

import com.company.logicstic.service.TenantDataSourceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.tenancy", name = "enabled", havingValue = "true")
public class TenantJwtClaimFilter extends OncePerRequestFilter {

    private static final String TENANT_CLAIM = "tenant";

    private final TenantDataSourceService tenantDataSourceService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = resolveTenantId();
            if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken
                    && !StringUtils.hasText(tenantId)) {
                SecurityResponses.write(request, response, 403, "INVALID_TENANT_CONTEXT", "Authenticated tenant is required");
                return;
            }
            if (StringUtils.hasText(tenantId)) {
                if (TenantContext.getTenantId().filter(bound -> !bound.equals(tenantId)).isPresent()) {
                    SecurityResponses.write(request, response, 403, "IDENTITY_TENANT_MISMATCH", "Authenticated tenant boundary does not match");
                    return;
                }
                TenantContext.setTenantId(tenantId);
                try {
                    tenantDataSourceService.ensureTenantDataSource(tenantId);
                } catch (IllegalArgumentException inactiveTenant) {
                    SecurityResponses.write(request, response, 403, "INVALID_TENANT_CONTEXT", "Authenticated tenant is not available");
                    return;
                } catch (IllegalStateException | org.springframework.dao.DataAccessException unavailable) {
                    SecurityResponses.write(request, response, 503, "TENANT_UNAVAILABLE", "Tenant persistence is unavailable");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getClaimAsString(TENANT_CLAIM);
        }
        return null;
    }
}
