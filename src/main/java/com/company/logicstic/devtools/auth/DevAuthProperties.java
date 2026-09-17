package com.company.logicstic.devtools.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.dev-auth")
public record DevAuthProperties(
    @NotBlank @Email String username,
    @NotBlank String password,
    @NotBlank String accessToken,
    @NotBlank String tenantId,
    @NotNull Duration tokenTtl) {

  public boolean credentialsMatch(String candidateUsername, String candidatePassword) {
    boolean usernameMatches = constantTimeEquals(username, candidateUsername);
    boolean passwordMatches = constantTimeEquals(password, candidatePassword);
    return usernameMatches & passwordMatches;
  }

  public boolean tokenMatches(String candidateToken) {
    return constantTimeEquals(accessToken, candidateToken);
  }

  private static boolean constantTimeEquals(String expected, String candidate) {
    byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
    byte[] candidateBytes =
        candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expectedBytes, candidateBytes);
  }
}
