package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.config.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LarkAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(LarkAuthenticationFilter.class);

  private final LarkAuthService authService;

  public LarkAuthenticationFilter(LarkAuthService authService) {
    this.authService = authService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");

    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
      String token = header.substring(7).trim();
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        try {
          Claims claims = authService.validateInternalToken(token);
          String email = claims.get("email", String.class);
          String tenant = claims.get("tenant", String.class);
          if (!StringUtils.hasText(claims.getSubject()) || !StringUtils.hasText(email)
              || !StringUtils.hasText(tenant)) {
            throw new IllegalArgumentException("Authenticated identity claims are incomplete");
          }
          if (TenantContext.getTenantId().filter(bound -> !bound.equals(tenant)).isPresent()) {
            // A disconnected client may make response writing fail; clear first.
            TenantContext.clear();
            com.company.logicstic.config.SecurityResponses.write(request, response, 403,
                "IDENTITY_TENANT_MISMATCH", "Authenticated tenant boundary does not match");
            return;
          }

          List<?> rawRoles = claims.get("roles", List.class);
          if (rawRoles != null && rawRoles.stream().anyMatch(role -> !(role instanceof String))) {
            throw new IllegalArgumentException("Identity roles are invalid");
          }
          List<String> roles = rawRoles == null ? Collections.emptyList() : rawRoles.stream().map(String.class::cast).toList();

          List<SimpleGrantedAuthority> authorities =
              roles.stream()
                  .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                  .map(SimpleGrantedAuthority::new)
                  .collect(Collectors.toList());

          // Retain the validated subject/tenant claims through the shared Spring
          // principal. Existing financial actor lookups still use the email name.
          Jwt principal = Jwt.withTokenValue(token)
              .header("typ", "JWT")
              .claims(values -> values.putAll(claims))
              .issuedAt(claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant())
              .expiresAt(claims.getExpiration() == null ? null : claims.getExpiration().toInstant())
              .build();
          JwtAuthenticationToken authentication =
              new JwtAuthenticationToken(principal, authorities, email);

          SecurityContextHolder.getContext().setAuthentication(authentication);

          if (StringUtils.hasText(tenant)) {
            TenantContext.setTenantId(tenant);
          }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
          SecurityContextHolder.clearContext();
          TenantContext.clear();
          com.company.logicstic.exception.ErrorLog.write(log, 401, "UNAUTHORIZED", ex, request);
          com.company.logicstic.config.SecurityResponses.write(request, response, 401, "UNAUTHORIZED", "Authentication token is invalid or expired");
          return;
        } catch (com.company.logicstic.exception.ApiException ex) {
          SecurityContextHolder.clearContext();
          TenantContext.clear();
          com.company.logicstic.exception.ErrorLog.write(log, ex.getStatus().value(), ex.getCode(), ex, request);
          com.company.logicstic.config.SecurityResponses.write(request, response, ex.getStatus().value(), ex.getCode(), ex.getMessage());
          return;
        } catch (RuntimeException ex) {
          SecurityContextHolder.clearContext();
          TenantContext.clear();
          com.company.logicstic.exception.ErrorLog.write(log, 500, "INTERNAL_ERROR", ex, request);
          com.company.logicstic.config.SecurityResponses.write(request, response, 500, "INTERNAL_ERROR", "An unexpected error occurred");
          return;
        }
      }
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      // Clean up thread-local tenant if set by this filter
      TenantContext.clear();
    }
  }
}
