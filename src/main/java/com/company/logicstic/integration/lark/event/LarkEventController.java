package com.company.logicstic.integration.lark.event;

import com.company.logicstic.exception.ApiException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LarkEventController {
  private final LarkEventSecurity security;
  private final LarkEventDispatcher dispatcher;

  public LarkEventController(LarkEventSecurity security, LarkEventDispatcher dispatcher) {
    this.security = security;
    this.dispatcher = dispatcher;
  }

  @PostMapping(value = "/api/lark/events", consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> receive(@RequestBody String rawBody, @RequestHeader HttpHeaders headers) {
    var payload = security.authenticate(rawBody, headers);
    if ("url_verification".equals(LarkEventSecurity.text(payload, "type"))) {
      String challenge = LarkEventSecurity.text(payload, "challenge");
      if (!StringUtils.hasText(challenge)) throw LarkEventSecurity.malformed();
      return Map.of("challenge", challenge);
    }
    var header = payload.path("header");
    String eventId = LarkEventSecurity.text(header, "event_id");
    String eventType = LarkEventSecurity.text(header, "event_type");
    if (!"2.0".equals(LarkEventSecurity.text(payload, "schema"))
        || !StringUtils.hasText(eventId) || eventId.length() > 255
        || !StringUtils.hasText(eventType) || eventType.length() > 255
        || !payload.path("event").isObject()) throw LarkEventSecurity.malformed();
    dispatcher.accept(eventId, eventType, payload.path("event"));
    return Map.of("msg", "ok");
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, String>> reject(ApiException failure) {
    // Keep parser/crypto/JDBC causes and all provider payloads out of error logs.
    return ResponseEntity.status(failure.getStatus()).body(Map.of("msg", failure.getCode()));
  }
}
