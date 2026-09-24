package com.company.logicstic;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public projection of the canonical Actuator readiness result including bootstrap metadata. */
@RestController
public class HealthController {

  private final HealthEndpoint healthEndpoint;
  private final String applicationName;
  private final Environment environment;

  public HealthController(
      HealthEndpoint healthEndpoint,
      @Value("${spring.application.name:logicstic}") String applicationName,
      Environment environment) {
    this.healthEndpoint = healthEndpoint;
    this.applicationName = applicationName;
    this.environment = environment;
  }

  @GetMapping({"/", "/health", "/api/health"})
  public ResponseEntity<Map<String, Object>> health() {
    HealthDescriptor health = healthEndpoint.health();
    String profiles = String.join(",", environment.getActiveProfiles());
    if (profiles.isBlank()) {
      profiles = "default";
    }
    String database = environment.acceptsProfiles(Profiles.of("nodb")) ? "disabled" : "enabled";
    return responseFor(health.getStatus(), applicationName, profiles, database);
  }

  static ResponseEntity<Map<String, Object>> responseFor(
      Status status, String application, String profiles, String database) {
    HttpStatus responseStatus =
        Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(responseStatus)
        .body(
            Map.of(
                "status", status.getCode(),
                "application", application,
                "profiles", profiles,
                "database", database));
  }
}
