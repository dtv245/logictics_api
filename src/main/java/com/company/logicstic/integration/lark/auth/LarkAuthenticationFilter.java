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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

          @SuppressWarnings("unchecked")
          List<String> roles = claims.get("roles", List.class);
          if (roles == null) {
            roles = Collections.emptyList();
          }

          List<SimpleGrantedAuthority> authorities =
              roles.stream()
                  .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                  .map(SimpleGrantedAuthority::new)
                  .collect(Collectors.toList());

          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(email, null, authorities);

          SecurityContextHolder.getContext().setAuthentication(authentication);

          if (StringUtils.hasText(tenant)) {
            TenantContext.setTenantId(tenant);
          }
        } catch (Exception ex) {
          // Token is not an internal Lark token or expired; ignore and continue filter chain
          log.trace("Token is not a valid Lark token: {}", ex.getMessage());
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
