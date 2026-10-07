package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.LarkUserMapping;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.LarkUserMappingRepository;
import com.company.logicstic.config.TenantContext;
import com.company.logicstic.service.TenantDataSourceService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

@Service
public class LarkAuthService {

  private static final Logger log = LoggerFactory.getLogger(LarkAuthService.class);
  private static final String HMAC_SHA256 = "HmacSHA256";

  private final LarkProperties properties;
  private final LarkAuthClient authClient;
  private final EmployeeRepository employeeRepository;
  private final LarkUserMappingRepository larkUserMappingRepository;
  private final byte[] secretKeyBytes;
  private final SecretKey jwtSecretKey;
  private final ObjectProvider<TenantDataSourceService> tenantDataSources;

  public LarkAuthService(
      LarkProperties properties,
      LarkAuthClient authClient,
      EmployeeRepository employeeRepository) {
    this(properties, authClient, employeeRepository, null, null);
  }

  public LarkAuthService(
      LarkProperties properties,
      LarkAuthClient authClient,
      EmployeeRepository employeeRepository,
      LarkUserMappingRepository larkUserMappingRepository) {
    this(properties, authClient, employeeRepository, larkUserMappingRepository, null);
  }

  public LarkAuthService(
      LarkProperties properties,
      LarkAuthClient authClient,
      EmployeeRepository employeeRepository,
      ObjectProvider<TenantDataSourceService> tenantDataSources) {
    this(properties, authClient, employeeRepository, null, tenantDataSources);
  }

  @Autowired
  public LarkAuthService(
      LarkProperties properties,
      LarkAuthClient authClient,
      @Autowired(required = false) EmployeeRepository employeeRepository,
      @Autowired(required = false) LarkUserMappingRepository larkUserMappingRepository,
      ObjectProvider<TenantDataSourceService> tenantDataSources) {
    this.properties = properties;
    this.authClient = authClient;
    this.employeeRepository = employeeRepository;
    this.larkUserMappingRepository = larkUserMappingRepository;
    this.tenantDataSources = tenantDataSources;
    this.secretKeyBytes = properties.enabled() ? deriveKeyBytes(properties.jwtSecret()) : null;
    this.jwtSecretKey = properties.enabled() ? Keys.hmacShaKeyFor(this.secretKeyBytes) : null;
  }

  private byte[] deriveKeyBytes(String rawSecret) {
    byte[] raw = rawSecret.getBytes(StandardCharsets.UTF_8);
    if (raw.length < 32) throw new IllegalArgumentException("Signing key must contain at least 32 UTF-8 bytes");
    return raw;
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
    requireEnabled();
    if (StringUtils.hasText(error)) {
      String desc = StringUtils.hasText(errorDescription) ? ": " + errorDescription : "";
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Lark login cancelled or rejected (" + error + desc + ")");
    }

    String validatedReturnTo = validateState(state);

    if (!StringUtils.hasText(code)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Authorization code is required");
    }

    String tenantId = resolveLoginTenant();

    LarkTokenResponse tokenResponse = authClient.exchangeCodeForToken(code, properties.redirectUri());
    LarkUserResponse larkUser = authClient.extractUser(tokenResponse);

    Optional<String> previousTenant = TenantContext.getTenantId();
    Employee employee;
    try {
      TenantContext.setTenantId(tenantId);
      employee = resolveEmployee(larkUser);
    } finally {
      previousTenant.ifPresentOrElse(TenantContext::setTenantId, TenantContext::clear);
    }

    String roleName = employee.getRole() != null && StringUtils.hasText(employee.getRole().getName())
        ? employee.getRole().getName().toUpperCase(Locale.ROOT)
        : "EMPLOYEE";
    String displayName = StringUtils.hasText(larkUser.displayName())
        ? larkUser.displayName()
        : (employee.getFirstName() + " " + employee.getLastName()).trim();
    String subject = StringUtils.hasText(larkUser.openId()) ? larkUser.openId().trim() : "lark:" + employee.getId();
    String resolvedEmail = StringUtils.hasText(employee.getEmail())
        ? employee.getEmail().trim().toLowerCase(Locale.ROOT)
        : (StringUtils.hasText(larkUser.effectiveEmail()) ? larkUser.effectiveEmail().trim().toLowerCase(Locale.ROOT) : "");

    String internalJwt = createInternalToken(subject, resolvedEmail, tenantId, List.of(roleName), displayName, employee.getId());

    String finalReturnTo = StringUtils.hasText(validatedReturnTo)
        ? validatedReturnTo
        : (StringUtils.hasText(requestedReturnTo) ? requestedReturnTo : "/");

    return new LarkLoginResult(
        internalJwt,
        "Bearer",
        properties.jwtTtl().toSeconds(),
        subject,
        resolvedEmail,
        tenantId,
        List.of(roleName),
        finalReturnTo,
        displayName,
        employee.getId());
  }

  private Employee resolveEmployee(LarkUserResponse larkUser) {
    if (larkUser == null) {
      log.warn("Lark authentication failed: empty user profile received");
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    // Token helpers remain available without persistence, but a login must create
    // or resolve a durable identity rather than silently fall back to email.
    if (employeeRepository == null || larkUserMappingRepository == null) {
      throw new ApiException(HttpStatus.FORBIDDEN, "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    // 1. Prefer existing mapping by openId or unionId
    Optional<LarkUserMapping> existingMapping = findExistingMapping(larkUser);
    if (existingMapping.isPresent()) {
      LarkUserMapping mapping = existingMapping.get();
      Employee mappedEmployee = mapping.getEmployee();
      if (mappedEmployee == null || !"ACTIVE".equalsIgnoreCase(mappedEmployee.getStatus())) {
        log.warn("Lark user [openId={}] is mapped to missing or inactive employee", sanitize(larkUser.openId()));
        throw new ApiException(
            HttpStatus.FORBIDDEN,
            "LARK_EMPLOYEE_NOT_LINKED",
            "No active employee is linked to this Lark account");
      }
      return mappedEmployee;
    }

    // 2. Auto-link fallback via email
    String email = larkUser.effectiveEmail();
    if (!StringUtils.hasText(email)) {
      log.warn("Lark user [openId={}] has no verified email address; auto-linking aborted", sanitize(larkUser.openId()));
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    List<Employee> matchingEmployees = employeeRepository.findAllByEmailForLarkLogin(normalizedEmail);

    if (matchingEmployees == null || matchingEmployees.isEmpty()) {
      log.warn("Lark user [openId={}] attempted login but email is not registered in system", sanitize(larkUser.openId()));
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    if (matchingEmployees.size() > 1) {
      log.warn("Multiple employees found matching email; auto-linking rejected to prevent ambiguous association");
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    Employee matchedEmployee = matchingEmployees.get(0);
    if (!"ACTIVE".equalsIgnoreCase(matchedEmployee.getStatus())) {
      log.warn("Employee matching email is not active (status=[{}])", matchedEmployee.getStatus());
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    // 3. Persist stable mapping for subsequent logins (handling race conditions)
    if (!StringUtils.hasText(larkUser.openId()) && !StringUtils.hasText(larkUser.unionId())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }

    LarkUserMapping newMapping = new LarkUserMapping();
    newMapping.setEmployee(matchedEmployee);
    newMapping.setOpenId(StringUtils.hasText(larkUser.openId()) ? larkUser.openId().trim() : null);
    newMapping.setUnionId(StringUtils.hasText(larkUser.unionId()) ? larkUser.unionId().trim() : null);
    newMapping.setLarkUserId(StringUtils.hasText(larkUser.userId()) ? larkUser.userId().trim() : null);
    newMapping.setEmail(normalizedEmail);

    try {
      larkUserMappingRepository.saveAndFlush(newMapping);
      log.info("Successfully linked Lark account [openId={}] to employee [{}]",
          sanitize(larkUser.openId()), matchedEmployee.getId());
    } catch (DataIntegrityViolationException ex) {
      log.info("Concurrent Lark user mapping insert detected for openId [{}], reloading existing mapping",
          sanitize(larkUser.openId()));
      Optional<LarkUserMapping> concurrent = findExistingMapping(larkUser);
      if (concurrent.isPresent()) {
        Employee concurrentEmp = concurrent.get().getEmployee();
        if (concurrentEmp != null && "ACTIVE".equalsIgnoreCase(concurrentEmp.getStatus())) {
          return concurrentEmp;
        }
      }
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "LARK_EMPLOYEE_NOT_LINKED",
          "No active employee is linked to this Lark account");
    }
    return matchedEmployee;
  }

  private Optional<LarkUserMapping> findExistingMapping(LarkUserResponse larkUser) {
    if (larkUserMappingRepository == null || larkUser == null) {
      return Optional.empty();
    }
    if (StringUtils.hasText(larkUser.openId())) {
      Optional<LarkUserMapping> byOpenId = larkUserMappingRepository.findByOpenIdWithEmployee(larkUser.openId().trim());
      if (byOpenId.isPresent()) {
        return byOpenId;
      }
    }
    if (StringUtils.hasText(larkUser.unionId())) {
      Optional<LarkUserMapping> byUnionId = larkUserMappingRepository.findByUnionIdWithEmployee(larkUser.unionId().trim());
      if (byUnionId.isPresent()) {
        return byUnionId;
      }
    }
    return Optional.empty();
  }

  private String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[\\r\\n]", "").trim();
  }

  private String resolveLoginTenant() {
    String tenant = properties.defaultTenantId();
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (TenantContext.getTenantId().filter(bound -> !bound.equals(tenant)).isPresent()
        || (authentication instanceof JwtAuthenticationToken jwt
            && !tenant.equals(jwt.getToken().getClaimAsString("tenant")))) {
      throw new ApiException(HttpStatus.FORBIDDEN, "IDENTITY_TENANT_MISMATCH",
          "Authenticated tenant does not match the configured login tenant");
    }
    TenantDataSourceService dataSources = tenantDataSources == null ? null : tenantDataSources.getIfAvailable();
    if (dataSources != null) {
      try {
        dataSources.ensureTenantDataSource(tenant);
      } catch (IllegalArgumentException inactive) {
        throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_TENANT_CONTEXT", "Login tenant is not available");
      } catch (IllegalStateException | org.springframework.dao.DataAccessException unavailable) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TENANT_UNAVAILABLE", "Login persistence is unavailable");
      }
    }
    return tenant;
  }

  public String createInternalToken(
      String subject,
      String email,
      String tenantId,
      List<String> roles,
      String displayName,
      UUID employeeId) {
    requireEnabled();
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
    requireEnabled();
    return Jwts.parser()
        .verifyWith(jwtSecretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private String computeHmac(String data) {
    requireEnabled();
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
      byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to compute HMAC", ex);
    }
  }

  private void requireEnabled() {
    if (!properties.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LARK_DISABLED", "Lark authentication is disabled");
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
