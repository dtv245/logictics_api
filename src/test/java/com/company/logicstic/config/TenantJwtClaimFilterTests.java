package com.company.logicstic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.company.logicstic.tenant.TenantDataSourceService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantJwtClaimFilterTests {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsTenantFromJwtClaimAndClearsItAfterRequestOnSameThread() throws Exception {
        TenantDataSourceService tenantDataSourceService = mock(TenantDataSourceService.class);
        TenantJwtClaimFilter filter = new TenantJwtClaimFilter(tenantDataSourceService);

        executeRequest(filter, "tenant-a", () -> assertThat(TenantContext.requireTenantId()).isEqualTo("tenant-a"));
        assertThat(TenantContext.getTenantId()).isEmpty();

        executeRequest(filter, "tenant-b", () -> assertThat(TenantContext.requireTenantId()).isEqualTo("tenant-b"));
        assertThat(TenantContext.getTenantId()).isEmpty();

        verify(tenantDataSourceService).ensureTenantDataSource("tenant-a");
        verify(tenantDataSourceService).ensureTenantDataSource("tenant-b");
    }

    private void executeRequest(TenantJwtClaimFilter filter, String tenantId, Runnable assertion) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(tenantId)));
        FilterChain filterChain = (request, response) -> assertion.run();
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), filterChain);
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(String tenantId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-" + tenantId)
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("tenant", tenantId)
                .build();
    }
}
