package com.company.logicstic.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight health endpoint that works with or without the database profile. */
@RestController
public class HealthController {

  private final String activeProfiles;

  public HealthController(@Value("${spring.profiles.active:}") String activeProfiles) {
    this.activeProfiles = activeProfiles;
  }

  @GetMapping({"/", "/health", "/api/health"})
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "UP");
    body.put("application", "logicstic");
    body.put(
        "profiles",
        activeProfiles == null || activeProfiles.isBlank() ? "default" : activeProfiles);
    body.put(
        "database",
        activeProfiles != null && activeProfiles.contains("nodb") ? "disabled" : "enabled");
    return ResponseEntity.ok(body);
  }
}
