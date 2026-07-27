package com.company.logicstic.shared.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

public class LogisticsJwtAuthenticationConverter
    implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {

  private static final String ROLE_PREFIX = "ROLE_";

  private final JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter =
      new JwtGrantedAuthoritiesConverter();

  @Override
  public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);
    if (scopeAuthorities != null) {
      authorities.addAll(scopeAuthorities);
    }

    addRoles(authorities, jwt.getClaim("role"));
    addRoles(authorities, jwt.getClaim("roles"));

    String principalName = jwt.getClaimAsString("email");
    if (!StringUtils.hasText(principalName)) {
      principalName = jwt.getSubject();
    }
    return new JwtAuthenticationToken(jwt, authorities, principalName);
  }

  private void addRoles(Set<GrantedAuthority> authorities, Object roleClaim) {
    if (roleClaim instanceof Collection<?> roles) {
      roles.forEach(role -> addRole(authorities, role));
      return;
    }
    addRole(authorities, roleClaim);
  }

  private void addRole(Set<GrantedAuthority> authorities, Object roleValue) {
    if (roleValue == null || !StringUtils.hasText(roleValue.toString())) {
      return;
    }
    String normalized =
        roleValue
            .toString()
            .trim()
            .replaceFirst("(?i)^ROLE_", "")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .toUpperCase(Locale.ROOT);
    if ("SUPER_ADMIN".equals(normalized)) {
      normalized = "SUPERADMIN";
    }
    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + normalized));
  }
}
