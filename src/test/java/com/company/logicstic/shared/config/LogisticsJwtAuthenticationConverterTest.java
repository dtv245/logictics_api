package com.company.logicstic.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LogisticsJwtAuthenticationConverterTest {

  private final LogisticsJwtAuthenticationConverter converter =
      new LogisticsJwtAuthenticationConverter();

  @Test
  void mapsDocumentedRoleAndEmailClaims() {
    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken)
            converter.convert(jwt("owner@example.com", "Owner", List.of("logisticsx.api")));

    assertThat(authentication.getName()).isEqualTo("owner@example.com");
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .contains("ROLE_OWNER", "SCOPE_logisticsx.api");
  }

  @Test
  void mapsRoleCollectionsAndNormalizesRoleNames() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("tenant", "default")
            .claim("roles", List.of("Super Admin", "ROLE_Dispatcher"))
            .build();

    JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

    assertThat(authentication.getName()).isEqualTo("user-1");
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .contains("ROLE_SUPERADMIN", "ROLE_DISPATCHER");
  }

  private Jwt jwt(String email, String role, List<String> scopes) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-1")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .claim("email", email)
        .claim("role", role)
        .claim("tenant", "default")
        .claim("scope", scopes)
        .build();
  }
}
