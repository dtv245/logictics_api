package com.company.logicstic.config;

import com.company.logicstic.integration.lark.auth.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unexpected token processing errors must remain 5xx and stop the filter chain. */
class AuthenticationFailureContractTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContext.clear();}
    @Test void invalidTokenAndSystemFailureHaveDifferentStatusesAndSharedIds() throws Exception {
        for(var failure: java.util.List.of(new io.jsonwebtoken.MalformedJwtException("private-token-detail"),new IllegalStateException("private-server-detail"))) {
            var auth=mock(LarkAuthService.class);when(auth.validateInternalToken("fixture")).thenThrow(failure);
            var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer fixture");
            var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
            new RequestIdFilter().doFilter(request,response,(req,res)->new LarkAuthenticationFilter(auth).doFilter(req,res,chain));
            assertThat(response.getStatus()).isEqualTo(failure instanceof io.jsonwebtoken.JwtException ? 401 : 500);
            assertThat(response.getContentAsString()).contains(response.getHeader(RequestIds.HEADER)).doesNotContain("private-");
            verifyNoInteractions(chain);assertThat(TenantContext.getTenantId()).isEmpty();
            assertThat(org.slf4j.MDC.get("requestId")).isNull();
        }
    }
    @Test void rejectedTenantIsClearedEvenWhenWritingTheResponseFails() throws Exception {
        var auth = mock(LarkAuthService.class);
        var claims = io.jsonwebtoken.Jwts.claims().subject("user").add("email", "user@example.test").add("tenant", "other").build();
        when(auth.validateInternalToken("fixture")).thenReturn(claims);
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer fixture");
        var response = mock(jakarta.servlet.http.HttpServletResponse.class);
        when(response.getOutputStream()).thenThrow(new java.io.IOException("disconnected"));
        TenantContext.setTenantId("bound");
        assertThatThrownBy(() -> new LarkAuthenticationFilter(auth).doFilter(request, response, mock(jakarta.servlet.FilterChain.class)))
                .isInstanceOf(java.io.IOException.class);
        assertThat(TenantContext.getTenantId()).isEmpty();
    }
}
