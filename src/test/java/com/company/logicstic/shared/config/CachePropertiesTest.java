package com.company.logicstic.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CacheProperties")
class CachePropertiesTest {

  @Test
  @DisplayName("caching is off unless a profile turns it on")
  void disabledByDefault() {
    assertThat(new CacheProperties().isEnabled()).isFalse();
  }

  @Test
  @DisplayName("returns the configured TTL for a known cache")
  void resolvesConfiguredTtl() {
    CacheProperties properties = new CacheProperties();
    properties.setTtl(Map.of("terminal", Duration.ofHours(24)));

    assertThat(properties.ttlFor("terminal")).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("falls back to the default TTL rather than caching forever")
  void unconfiguredCacheFallsBackToTheDefault() {
    CacheProperties properties = new CacheProperties();
    properties.setDefaultTtl(Duration.ofMinutes(3));

    // A missing entry must never mean "no expiry": the TTL is the safety net for a change made
    // outside the owning service, so an unbounded entry would hide such a change permanently.
    assertThat(properties.ttlFor("not-configured")).isEqualTo(Duration.ofMinutes(3));
    assertThat(properties.ttlFor("not-configured")).isPositive();
  }

  @Test
  @DisplayName("the key prefix defaults to the application name")
  void hasAnApplicationKeyPrefix() {
    assertThat(new CacheProperties().getKeyPrefix()).isEqualTo("logistics");
  }
}
