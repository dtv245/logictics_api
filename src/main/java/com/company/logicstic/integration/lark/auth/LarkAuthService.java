package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LarkAuthService {

  private static final Logger log = LoggerFactory.getLogger(LarkAuthService.class);
  private static final String HMAC_SHA256 = "HmacSHA256";

  private final LarkProperties properties;
  private final LarkAuthClient authClient;
  private final EmployeeRepository employeeRepository;
  private final byte[] secretKeyBytes;
  private final SecretKey jwtSecretKey;

  public LarkAuthService(
      LarkProperties properties,
      LarkAuthClient authClient,
      EmployeeRepository employeeRepository) {
    this.properties = properties;
    this.authClient = authClient;
    this.employeeRepository = employeeRepository;
    this.secretKeyBytes = deriveKeyBytes(properties.jwtSecret());
    this.jwtSecretKey = Keys.hmacShaKeyFor(this.secretKeyBytes);
  }

  private byte[] deriveKeyBytes(String rawSecret) {
    byte[] raw = rawSecret.getBytes(StandardCharsets.UTF_8);
    if (raw.length >= 32) {
      return raw;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(raw);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to derive secret key", ex);
    }
  }

  public String createState(String returnTo) {
    String nonce = UUID.randomUUID().toString();
    long timestamp = System.currentTimeMillis();
    String safeReturnTo = StringUtils.hasText(returnTo) ? returnTo.trim() : "";
    String encodedReturnTo =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(safeReturnTo.getBytes(StandardCharsets.UTF_8));

    String data = nonce + ":" + timestamp + ":" + encodedReturnTo;
    String signature = computeHmac(data);
    String rawState = data + ":" + signature;

    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(rawState.getBytes(StandardCharsets.UTF_8));
  }

  public String validateState(String state) {
    if (!StringUtils.hasText(state)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Missing state parameter");
    }

    try {
      String rawState =
          new String(Base64.getUrlDecoder().decode(state.trim()), StandardCharsets.UTF_8);
      String[] parts = rawState.split(":", 4);
      if (parts.length != 4) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed state parameter");
      }

      String nonce = parts[0];
      long timestamp = Long.parseLong(parts[1]);
      String encodedReturnTo = parts[2];
      String providedSignature = parts[3];

      String data = nonce + ":" + timestamp + ":" + encodedReturnTo;
      String expectedSignature = computeHmac(data);
      if (!MessageDigest.isEqual(
          providedSignature.getBytes(StandardCharsets.UTF_8),
          expectedSignature.getBytes(StandardCharsets.UTF_8))) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid state signature");
      }

      long elapsed = System.currentTimeMillis() - timestamp;
      if (elapsed < 0 || elapsed > properties.stateTtl().toMillis()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "State parameter has expired");
      }

      byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedReturnTo);
      return new String(decodedBytes, StandardCharsets.UTF_8);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("State parameter validation failed: {}", ex.getMessage());
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid state parameter");
    }
  }

  public String getAuthorizeUrl(String returnTo) {
    String state = createState(returnTo);
    return authClient.buildAuthorizeUrl(state, properties.redirectUri());
  }

  public LarkLoginResult handleCallback(
      String code,
      String state,
      String error,
      String errorDescription,
      String requestedReturnTo) {
    if (StringUtils.hasText(error)) {
      String desc = StringUtils.hasText(errorDescription) ? ": " + errorDescription : "";
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Lark login cancelled or rejected (" + error + desc + ")");
    }

    String validatedReturnTo = validateState(state);

    if (!StringUtils.hasText(code)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Authorization code is required");
    }

    LarkTokenResponse tokenResponse = authClient.exchangeCodeForToken(code, properties.redirectUri());
    LarkUserResponse larkUser = authClient.extractUser(tokenResponse);

    String email = larkUser.effectiveEmail();
    if (!StringUtils.hasText(email)) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Lark user account does not contain a verified email address");
    }

    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    Optional<Employee> employeeOpt = employeeRepository.findByEmail(normalizedEmail);

    if (employeeOpt.isEmpty()) {
      log.warn("Lark user [{}] attempted login but is not registered in system", normalizedEmail);
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "ACCESS_DENIED",
          "Tài khoản Lark [" + normalizedEmail + "] chưa được liên kết với nhân viên nào trong hệ thống.");
    }

    Employee employee = employeeOpt.get();
    if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
      log.warn("Lark user [{}] matched inactive employee status [{}]", normalizedEmail, employee.getStatus());
      throw new ApiException(
          HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Tài khoản nhân viên [" + normalizedEmail + "] đang bị khóa hoặc không hoạt động.");
    }

    String roleName = employee.getRole() != null && StringUtils.hasText(employee.getRole().getName())
        ? employee.getRole().getName().toUpperCase(Locale.ROOT)
        : "EMPLOYEE";
    String tenantId = properties.defaultTenantId();
    String displayName = StringUtils.hasText(larkUser.displayName())
        ? larkUser.displayName()
        : (employee.getFirstName() + " " + employee.getLastName()).trim();
    String subject = StringUtils.hasText(larkUser.openId()) ? larkUser.openId() : "lark:" + employee.getId();

    String internalJwt = createInternalToken(subject, normalizedEmail, tenantId, List.of(roleName), displayName, employee.getId());

    String finalReturnTo = StringUtils.hasText(validatedReturnTo)
        ? validatedReturnTo
        : (StringUtils.hasText(requestedReturnTo) ? requestedReturnTo : "/");

    return new LarkLoginResult(
        internalJwt,
        "Bearer",
        properties.jwtTtl().toSeconds(),
        subject,
        normalizedEmail,
        tenantId,
        List.of(roleName),
        finalReturnTo,
        displayName,
        employee.getId());
  }

  public String createInternalToken(
      String subject,
      String email,
      String tenantId,
      List<String> roles,
      String displayName,
      UUID employeeId) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.jwtTtl());

    var builder = Jwts.builder()
        .issuer(properties.jwtIssuer())
        .subject(subject)
        .claim("email", email)
        .claim("tenant", tenantId)
        .claim("roles", roles)
        .claim("name", displayName)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt));

    if (employeeId != null) {
      builder.claim("employee_id", employeeId.toString());
    }

    return builder.signWith(jwtSecretKey).compact();
  }

  public Claims validateInternalToken(String token) {
    return Jwts.parser()
        .verifyWith(jwtSecretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private String computeHmac(String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to compute HMAC", ex);
    }
  }

  public record LarkLoginResult(
      String accessToken,
      String tokenType,
      long expiresIn,
      String subject,
      String email,
      String tenantId,
      List<String> roles,
      String returnTo,
      String name,
      UUID employeeId) {}
}
