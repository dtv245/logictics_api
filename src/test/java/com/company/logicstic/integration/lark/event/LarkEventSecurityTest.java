package com.company.logicstic.integration.lark.event;

import com.company.logicstic.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LarkEventSecurityTest {
  @Test void rejectsStaleFutureMalformedAndMillisecondRequestTimestamps() throws Exception {
    var clock = new MutableLarkEventClock();
    var settings = LarkEventProcessingProperties.defaults();
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON, settings, clock);
    String raw = encrypted(challenge(TOKEN), KEY);
    long now = clock.instant().getEpochSecond();
    for (String timestamp : new String[]{Long.toString(now - 28801), Long.toString(now + 301),
        Long.toString(now * 1000), "not-a-time", "-1"})
      assertStatus(401, () -> security.authenticate(raw, signed(raw, KEY, timestamp)));
    assertTrue(security.authenticate(raw, signed(raw, KEY, Long.toString(now - 28800))).isObject());
    assertTrue(security.authenticate(raw, signed(raw, KEY, Long.toString(now + 300))).isObject());
  }

  @Test void acceptsCorrectSignatureOnOriginalEncryptedBodyAndRoundTripsUtf8() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    String raw = encrypted(challenge(TOKEN), KEY);
    var decoded = security.authenticate(raw, signed(raw, KEY));
    assertEquals("challenge-test-đúng", decoded.path("challenge").asString());
  }

  @Test void signatureMustMatchExactRawBodyIncludingWhitespace() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    String raw = encrypted(challenge(TOKEN), KEY);
    assertStatus(401, () -> security.authenticate(raw + " ", signed(raw, KEY)));
  }

  @Test void rejectsIncorrectSignatureBeforeParsingOrDecrypting() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    var headers = signed("not-json", KEY);
    headers.set("X-Lark-Signature", "0".repeat(64));
    assertStatus(401, () -> security.authenticate("not-json", headers));
  }

  @Test void rejectsEveryMissingSignatureHeader() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    String raw = encrypted(challenge(TOKEN), KEY);
    for (String name : new String[]{"X-Lark-Signature", "X-Lark-Request-Timestamp", "X-Lark-Request-Nonce"}) {
      var headers = signed(raw, KEY);
      headers.remove(name);
      assertStatus(401, () -> security.authenticate(raw, headers));
    }
  }

  @Test void rejectsInvalidHexOrWrongEncryptionKey() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    String raw = encrypted(challenge(TOKEN), TOKEN);
    assertStatus(401, () -> security.authenticate(raw, signed(raw, KEY)));
    var invalidHex = signed(raw, KEY);
    invalidHex.set("X-Lark-Signature", "z".repeat(64));
    assertStatus(401, () -> security.authenticate(raw, invalidHex));
  }

  @Test void rejectsTruncatedOrInvalidBase64CiphertextWithValidSignature() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    for (String value : new String[]{"not-base64!", "AA=="}) {
      String raw = JSON.createObjectNode().put("encrypt", value).toString();
      assertStatus(401, () -> security.authenticate(raw, signed(raw, KEY)));
    }
  }

  @Test void validatesChallengeTokenAndV2HeaderTokenInTokenOnlyMode() {
    var security = new LarkEventSecurity(properties(TOKEN, ""), JSON);
    assertEquals("url_verification", security.authenticate(challenge(TOKEN), new HttpHeaders()).path("type").asString());
    assertEquals("event-one", security.authenticate(event("event-one", TOKEN), new HttpHeaders())
        .path("header").path("event_id").asString());
    assertStatus(401, () -> security.authenticate(challenge("incorrect"), new HttpHeaders()));
    assertStatus(401, () -> security.authenticate(event("event-one", "incorrect"), new HttpHeaders()));
  }

  @Test void requiresBothCredentialsWhenBothConfigured() throws Exception {
    var security = new LarkEventSecurity(properties(TOKEN, KEY), JSON);
    String raw = encrypted(event("event-one", "incorrect"), KEY);
    assertStatus(401, () -> security.authenticate(raw, signed(raw, KEY)));
  }

  @Test void permitsEncryptKeyOnlyAndRejectsUnsignedPlaintext() throws Exception {
    var security = new LarkEventSecurity(properties("", KEY), JSON);
    String raw = encrypted(event("event-one", ""), KEY);
    assertTrue(security.authenticate(raw, signed(raw, KEY)).path("event").isObject());
    assertStatus(401, () -> security.authenticate(event("event-one", ""), new HttpHeaders()));
  }

  @Test void failsClosedWithMissingCredentialsAndDoesNotParseBody() {
    var security = new LarkEventSecurity(properties("", ""), JSON);
    assertStatus(503, () -> security.authenticate("not-json", new HttpHeaders()));
  }

  @Test void rejectsMalformedJsonAndEncryptedBodyWithoutKey() {
    var security = new LarkEventSecurity(properties(TOKEN, ""), JSON);
    for (String raw : new String[]{"not-json", "[]", "null"})
      assertStatus(400, () -> security.authenticate(raw, new HttpHeaders()));
    assertStatus(401, () -> security.authenticate("{\"encrypt\":\"AA==\"}", new HttpHeaders()));
  }

  private static void assertStatus(int status, org.junit.jupiter.api.function.Executable action) {
    ApiException failure = assertThrows(ApiException.class, action);
    assertEquals(status, failure.getStatus().value());
  }
}
