package com.company.logicstic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import com.company.logicstic.service.TenantDataSourceService;
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

    @Test void mismatchedAndInactiveTenantsFailClosedAndClearReusedThread() throws Exception {
        var service = mock(TenantDataSourceService.class);
        var filter = new TenantJwtClaimFilter(service);
        var chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt("tenant-a")));
        TenantContext.setTenantId("tenant-b");
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);assertThat(TenantContext.getTenantId()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(service, chain);
        org.mockito.Mockito.when(service.ensureTenantDataSource("tenant-a")).thenThrow(new IllegalArgumentException("Inactive fixture"));
        response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, chain);
        assertThat(response.getStatus()).isEqualTo(403);assertThat(TenantContext.getTenantId()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(chain);
    }

    @Test void unavailableRegistryRejects503WithoutLeakingDetailsAndClearsThread() throws Exception {
        var service = mock(TenantDataSourceService.class);
        org.mockito.Mockito.when(service.ensureTenantDataSource("tenant-a"))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private-fixture-detail"));
        var filter = new TenantJwtClaimFilter(service);
        var chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt("tenant-a")));
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("TENANT_UNAVAILABLE").doesNotContain("private-fixture-detail");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(TenantContext.getTenantId()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(chain);
        executeRequest(filter, "tenant-b", () -> assertThat(TenantContext.requireTenantId()).isEqualTo("tenant-b"));
        assertThat(TenantContext.getTenantId()).isEmpty();
    }
}
