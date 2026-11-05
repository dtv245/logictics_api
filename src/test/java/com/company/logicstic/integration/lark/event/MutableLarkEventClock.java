package com.company.logicstic.integration.lark.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class MutableLarkEventClock extends Clock {
  private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");
  void advance(Duration duration) { now = now.plus(duration); }
  @Override public Instant instant() { return now; }
  @Override public ZoneId getZone() { return ZoneOffset.UTC; }
  @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
}
