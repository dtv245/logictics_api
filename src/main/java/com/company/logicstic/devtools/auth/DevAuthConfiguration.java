package com.company.logicstic.devtools.auth;

import com.company.logicstic.security.SecurityJwtProperties;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@Profile("dev-auth")
@EnableConfigurationProperties(DevAuthProperties.class)
public class DevAuthConfiguration {

  static final String SUBJECT = "local-superadmin";
  static final String ROLE = "SUPERADMIN";

  @Bean
  JwtDecoder devAuthJwtDecoder(
      DevAuthProperties devAuthProperties, SecurityJwtProperties jwtProperties) {
    return token -> {
      if (!devAuthProperties.tokenMatches(token)) {
        throw new BadJwtException("Invalid local development token");
      }

      Instant issuedAt = Instant.now();
      return Jwt.withTokenValue(token)
          .header("alg", "dev-auth")
          .issuer(jwtProperties.issuer())
          .audience(List.of(jwtProperties.audience()))
          .subject(SUBJECT)
          .issuedAt(issuedAt)
          .expiresAt(issuedAt.plus(devAuthProperties.tokenTtl()))
          .claim("email", devAuthProperties.username())
          .claim("tenant", devAuthProperties.tenantId())
          .claim("roles", List.of(ROLE))
          .build();
    };
  }
}
