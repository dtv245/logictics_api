package com.company.logicstic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpStatus;

class HealthControllerTest {

  @Test
  void returnsOkOnlyWhenActuatorReportsUp() {
    var response = HealthController.responseFor(Status.UP, "logicstic", "local", "enabled");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("UP", response.getBody().get("status"));
    assertEquals("logicstic", response.getBody().get("application"));
    assertEquals("local", response.getBody().get("profiles"));
    assertEquals("enabled", response.getBody().get("database"));
  }

  @Test
  void returnsServiceUnavailableWhenActuatorReportsDown() {
    var response = HealthController.responseFor(Status.DOWN, "logicstic", "local", "enabled");

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals("DOWN", response.getBody().get("status"));
  }
}
