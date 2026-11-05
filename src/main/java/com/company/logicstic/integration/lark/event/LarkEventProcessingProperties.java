package com.company.logicstic.integration.lark.event;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.lark.events")
public record LarkEventProcessingProperties(Duration signatureMaxAge, Duration futureClockSkew,
    Duration leaseDuration, Duration pollInterval, Duration initialRetryDelay, Duration maxRetryDelay,
    Duration deadRetention, int maxAttempts, int batchSize, int cleanupBatchSize) {
  public LarkEventProcessingProperties {
    // The documented delivery retry window is about 7.1 hours; do not assume a
    // provider retry refreshes its original request timestamp.
    signatureMaxAge = positive(signatureMaxAge, Duration.ofHours(8));
    futureClockSkew = positive(futureClockSkew, Duration.ofMinutes(5));
    leaseDuration = positive(leaseDuration, Duration.ofMinutes(2));
    pollInterval = positive(pollInterval, Duration.ofSeconds(1));
    initialRetryDelay = positive(initialRetryDelay, Duration.ofSeconds(5));
    maxRetryDelay = positive(maxRetryDelay, Duration.ofMinutes(10));
    deadRetention = positive(deadRetention, Duration.ofDays(7));
    if (maxRetryDelay.compareTo(initialRetryDelay) < 0)
      throw new IllegalArgumentException("Lark event retry maximum must cover the initial delay");
    maxAttempts = maxAttempts == 0 ? 10 : maxAttempts;
    batchSize = batchSize == 0 ? 16 : batchSize;
    cleanupBatchSize = cleanupBatchSize == 0 ? 100 : cleanupBatchSize;
    if (maxAttempts < 1 || maxAttempts > 100 || batchSize < 1 || batchSize > 100
        || cleanupBatchSize < 1 || cleanupBatchSize > 1000)
      throw new IllegalArgumentException("Lark event processing limits are invalid");
  }

  public static LarkEventProcessingProperties defaults() {
    return new LarkEventProcessingProperties(null, null, null, null, null, null, null, 0, 0, 0);
  }

  private static Duration positive(Duration value, Duration fallback) {
    Duration actual = value == null ? fallback : value;
    if (actual.isNegative() || actual.isZero() || actual.toMillis() < 1)
      throw new IllegalArgumentException("Lark event durations must be positive");
    return actual;
  }
}
