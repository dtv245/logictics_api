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
    if (enabled && (jwtSecret == null || jwtSecret.isBlank()
        || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32
        || jwtSecret.startsWith("logistics-lark-default-secret-key-")
        || jwtSecret.startsWith("your-secure-jwt-secret-")))
      throw new IllegalArgumentException("LARK_JWT_SECRET must be explicitly configured with at least 32 UTF-8 bytes; shared defaults are forbidden");
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
