package com.company.logicstic.integration.lark.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.lark")
public record LarkProperties(
    boolean enabled,
    String appId,
    String appSecret,
    String redirectUri,
    String baseUrl,
    String jwtSecret,
    String jwtIssuer,
    Duration jwtTtl,
    Duration stateTtl,
    String defaultTenantId) {

  public LarkProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://open.larksuite.com";
    }
    if (jwtIssuer == null || jwtIssuer.isBlank()) {
      jwtIssuer = "https://open.larksuite.com";
    }
    if (jwtSecret == null || jwtSecret.isBlank()) {
      jwtSecret = "logistics-lark-default-secret-key-at-least-256-bits-long-change-in-production!";
    }
    if (jwtTtl == null) {
      jwtTtl = Duration.ofHours(8);
    }
    if (stateTtl == null) {
      stateTtl = Duration.ofMinutes(15);
    }
    if (defaultTenantId == null || defaultTenantId.isBlank()) {
      defaultTenantId = "local-development";
    }
  }
}
