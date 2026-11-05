package com.company.logicstic.integration.lark.event;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LarkEventSecurity {
  private static final Logger log = LoggerFactory.getLogger(LarkEventSecurity.class);
  private final LarkProperties properties;
  private final ObjectMapper json;
  private final LarkEventProcessingProperties processing;
  private final Clock clock;

  public LarkEventSecurity(LarkProperties properties, ObjectMapper json) {
    this(properties, json, LarkEventProcessingProperties.defaults(), Clock.systemUTC());
  }

  @Autowired
  public LarkEventSecurity(LarkProperties properties, ObjectMapper json, LarkEventProcessingProperties processing) {
    this(properties, json, processing, Clock.systemUTC());
  }

  LarkEventSecurity(LarkProperties properties, ObjectMapper json, LarkEventProcessingProperties processing, Clock clock) {
    this.properties = properties;
    this.json = json;
    this.processing = processing;
    this.clock = clock;
  }

  @PostConstruct
  void warnIfDisabled() {
    if (!configured()) log.warn("Lark event receiver disabled: no event verification credential configured");
  }

  public JsonNode authenticate(String rawBody, HttpHeaders headers) {
    if (!configured()) throw unavailable();
    // The signed input is the original UTF-8 request, never the decrypted JSON.
    if (StringUtils.hasText(properties.encryptKey())) verifySignature(rawBody, headers);
    JsonNode payload = readObject(rawBody);
    if (StringUtils.hasText(properties.encryptKey())) {
      String encrypted = text(payload, "encrypt");
      if (encrypted == null) throw unauthorized();
      try {
        payload = readObject(decrypt(encrypted));
      } catch (ApiException invalidCiphertext) {
        throw unauthorized();
      }
    } else if (payload.has("encrypt")) {
      throw unauthorized();
    }
    if (StringUtils.hasText(properties.verificationToken())) {
      JsonNode tokenOwner = "url_verification".equals(text(payload, "type"))
          ? payload : payload.path("header");
      String supplied = text(tokenOwner, "token");
      if (supplied == null || !MessageDigest.isEqual(
          sha256(properties.verificationToken()), sha256(supplied))) throw unauthorized();
    }
    return payload;
  }

  void verifySignature(String rawBody, HttpHeaders headers) {
    String timestamp = headers.getFirst("X-Lark-Request-Timestamp");
    String nonce = headers.getFirst("X-Lark-Request-Nonce");
    String signature = headers.getFirst("X-Lark-Signature");
    if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)
        || signature == null || !signature.matches("[0-9a-f]{64}")) throw unauthorized();
    byte[] expected = sha256(timestamp + nonce + properties.encryptKey() + rawBody);
    if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) throw unauthorized();
    try {
      // Request timestamp is Unix seconds, not header.create_time. Eight hours
      // covers Lark's documented ~7.1-hour retry window even if headers are reused.
      if (!timestamp.matches("[0-9]{1,12}")) throw unauthorized();
      Instant requestedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
      Instant now = clock.instant();
      if (requestedAt.isBefore(now.minus(processing.signatureMaxAge()))
          || requestedAt.isAfter(now.plus(processing.futureClockSkew()))) throw unauthorized();
    } catch (DateTimeException | NumberFormatException invalidTime) {
      throw unauthorized();
    }
  }

  String decrypt(String encrypted) {
    try {
      byte[] bytes = Base64.getDecoder().decode(encrypted);
      if (bytes.length < 32 || (bytes.length - 16) % 16 != 0) throw unauthorized();
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sha256(properties.encryptKey()), "AES"),
          new IvParameterSpec(Arrays.copyOfRange(bytes, 0, 16)));
      return new String(cipher.doFinal(bytes, 16, bytes.length - 16), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException invalid) {
      // Never expose crypto exceptions, the request body or credential values.
      throw unauthorized();
    }
  }

  private JsonNode readObject(String value) {
    try {
      JsonNode node = json.readTree(value);
      if (node == null || !node.isObject()) throw malformed();
      return node;
    } catch (JacksonException invalid) {
      throw malformed();
    }
  }

  private boolean configured() {
    return StringUtils.hasText(properties.verificationToken()) || StringUtils.hasText(properties.encryptKey());
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable");
    }
  }

  static String text(JsonNode node, String key) {
    JsonNode value = node.path(key);
    return value.isTextual() ? value.asString() : null;
  }

  static ApiException unauthorized() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "LARK_EVENT_UNAUTHORIZED", "Event authentication failed");
  }

  static ApiException unavailable() {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LARK_EVENT_UNAVAILABLE", "Event receiver unavailable");
  }

  static ApiException malformed() {
    return new ApiException(HttpStatus.BAD_REQUEST, "LARK_EVENT_INVALID", "Invalid event envelope");
  }
}
