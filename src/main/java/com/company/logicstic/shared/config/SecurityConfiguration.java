package com.company.logicstic.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityJwtProperties.class)
public class SecurityConfiguration {

  private static final String[] OWNER_ROLES = {"SUPERADMIN", "OWNER"};
  private static final String[] MANAGEMENT_ROLES = {"SUPERADMIN", "OWNER", "MANAGER"};
  private static final String[] OPERATIONS_ROLES = {"SUPERADMIN", "OWNER", "MANAGER", "DISPATCHER"};
  private static final String[] TENANT_ROLES = {
    "SUPERADMIN", "OWNER", "MANAGER", "DISPATCHER", "DRIVER"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      LogisticsJwtAuthenticationConverter jwtAuthenticationConverter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/health",
                        "/api/health",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/error")
                    .permitAll()
                    .requestMatchers("/api/roles/**", "/api/employees/**")
                    .hasAnyRole(OWNER_ROLES)
                    .requestMatchers("/api/customers/**")
                    .hasAnyRole(MANAGEMENT_ROLES)
                    .requestMatchers(HttpMethod.GET, "/api/invoices/**", "/api/payments/**")
                    .hasAnyRole(OPERATIONS_ROLES)
                    .requestMatchers("/api/invoices/**", "/api/payments/**")
                    .hasAnyRole(MANAGEMENT_ROLES)
                    .requestMatchers(HttpMethod.GET, "/api/loads/**", "/api/trips/**")
                    .hasAnyRole(TENANT_ROLES)
                    .requestMatchers(
                        HttpMethod.POST, "/api/loads/*/pick-up", "/api/loads/*/deliver")
                    .hasAnyRole(TENANT_ROLES)
                    .requestMatchers("/api/loads/**", "/api/trips/**")
                    .hasAnyRole(OPERATIONS_ROLES)
                    .requestMatchers(HttpMethod.GET, "/api/documents/**")
                    .hasAnyRole(TENANT_ROLES)
                    .requestMatchers(HttpMethod.POST, "/api/documents")
                    .hasAnyRole(TENANT_ROLES)
                    .requestMatchers("/api/documents/**")
                    .hasAnyRole(OPERATIONS_ROLES)
                    .requestMatchers("/api/drivers/**", "/api/trucks/**")
                    .hasAnyRole(OPERATIONS_ROLES)
                    .requestMatchers(HttpMethod.GET, "/api/terminals/**")
                    .hasAnyRole(TENANT_ROLES)
                    .requestMatchers("/api/terminals/**")
                    .hasAnyRole(OPERATIONS_ROLES)
                    .requestMatchers(
                        "/api/inspections/**", "/api/messages/**", "/api/notifications/**")
                    .hasAnyRole(TENANT_ROLES)
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  @Bean
  LogisticsJwtAuthenticationConverter jwtAuthenticationConverter() {
    return new LogisticsJwtAuthenticationConverter();
  }

  @Bean
  JwtDecoder jwtDecoder(SecurityJwtProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> issuerValidator =
        JwtValidators.createDefaultWithIssuer(properties.issuer());
    OAuth2TokenValidator<Jwt> audienceValidator =
        token ->
            token.getAudience().contains(properties.audience())
                ? OAuth2TokenValidatorResult.success()
                : validationFailure("JWT audience does not include " + properties.audience());
    OAuth2TokenValidator<Jwt> tenantValidator =
        token ->
            token.hasClaim("tenant")
                    && token.getClaimAsString("tenant") != null
                    && !token.getClaimAsString("tenant").isBlank()
                ? OAuth2TokenValidatorResult.success()
                : validationFailure("JWT tenant claim is required");
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            List.of(issuerValidator, audienceValidator, tenantValidator)));
    return decoder;
  }

  private OAuth2TokenValidatorResult validationFailure(String description) {
    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
  }
}
