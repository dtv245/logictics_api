package com.company.logicstic.integration.lark.event;

import com.company.logicstic.integration.lark.config.LarkProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class LarkEventTestSupport {
  static final ObjectMapper JSON = JsonMapper.builder().build();
  static final String TOKEN = UUID.randomUUID().toString();
  static final String KEY = UUID.randomUUID().toString();

  static LarkProperties properties(String token, String key) {
    return new LarkProperties(false, null, null, null, null, null, null, null, null,
        "event-test-tenant", token, key);
  }

  static String challenge(String token) {
    return JSON.createObjectNode().put("type", "url_verification")
        .put("challenge", "challenge-test-đúng").put("token", token).toString();
  }

  static String event(String eventId, String token) {
    var payload = JSON.createObjectNode().put("schema", "2.0");
    payload.putObject("header").put("event_id", eventId)
        .put("event_type", LarkBaseRecordChangedHandler.EVENT_TYPE).put("token", token);
    payload.putObject("event");
    return payload.toString();
  }

  static HttpHeaders signed(String body, String key) throws Exception {
    var headers = new HttpHeaders();
    return signed(body, key, Long.toString(java.time.Instant.now().getEpochSecond()));
  }

  static HttpHeaders signed(String body, String key, String timestamp) throws Exception {
    var headers = new HttpHeaders();
    String nonce = "test-nonce";
    byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest((timestamp + nonce + key + body).getBytes(StandardCharsets.UTF_8));
    headers.set("X-Lark-Request-Timestamp", timestamp);
    headers.set("X-Lark-Request-Nonce", nonce);
    headers.set("X-Lark-Signature", HexFormat.of().formatHex(digest));
    return headers;
  }

  static String encrypted(String payload, String key) throws Exception {
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
    byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    byte[] envelope = new byte[iv.length + ciphertext.length];
    System.arraycopy(iv, 0, envelope, 0, iv.length);
    System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
    return JSON.createObjectNode().put("encrypt", Base64.getEncoder().encodeToString(envelope)).toString();
  }

  static DriverManagerDataSource database() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:lark_events_" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    new ResourceDatabasePopulator(new ClassPathResource(
        "db/migration/tenant/V41__create_lark_event_receipts.sql"), new ClassPathResource(
        "db/migration/tenant/V42__persist_lark_event_inbox.sql")).execute(source);
    // H2 2.4.240 closes its in-memory Database while reusing IN-check expressions after DDL.
    // PostgreSQL migration tests cover these production constraints; the H2 fixture exercises inbox behavior.
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("ALTER TABLE lark_event_receipts DROP CONSTRAINT ck_lark_event_receipt_status");
    jdbc.execute("ALTER TABLE lark_event_receipts DROP CONSTRAINT ck_lark_event_receipt_pending_payload");
    return source;
  }
}
