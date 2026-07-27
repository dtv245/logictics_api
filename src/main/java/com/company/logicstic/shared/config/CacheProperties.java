package com.company.logicstic.shared.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis cache settings, bound from {@code app.cache}
 * (docs/docs/development/engineering-conventions.md §11).
 *
 * <p>Mirrors {@link TenancyProperties}: an {@code enabled} flag that every cache bean is
 * {@code @ConditionalOnProperty} on, so the feature is switched off by configuration rather than by
 * commenting a block out. It defaults to {@code false} — caching is opt-in per environment, and can
 * be turned off during an incident without a redeploy.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

  private boolean enabled;

  /**
   * First segment of every Redis key, so this application's entries are recognisable and can be
   * scanned or dropped without touching another application sharing the instance.
   */
  private String keyPrefix = "logistics";

  /** How long a cached entry survives, keyed by cache name. Falls back to {@link #defaultTtl}. */
  private Map<String, Duration> ttl = new LinkedHashMap<>();

  /** Used for a cache that has no explicit entry in {@link #ttl}. */
  private Duration defaultTtl = Duration.ofMinutes(10);

  /**
   * Resolves the TTL for a cache, falling back to {@link #defaultTtl} when it is not configured.
   *
   * @param cacheName the Spring cache name, e.g. {@code terminal}
   */
  public Duration ttlFor(String cacheName) {
    return ttl.getOrDefault(cacheName, defaultTtl);
  }
}
