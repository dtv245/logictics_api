package com.company.logicstic.integration.lark.event;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static com.company.logicstic.integration.lark.event.LarkEventTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LarkEventDeduplicatorTest {
  @Test void databaseConstraintDeduplicatesAcrossInstancesAndThreads() throws Exception {
    var source = database();
    var one = new LarkEventDeduplicator(source);
    var two = new LarkEventDeduplicator(source);
    var calls = new ArrayList<Callable<Optional<LarkEventDeduplicator.Claim>>>();
    for (int i = 0; i < 16; i++) {
      var instance = i % 2 == 0 ? one : two;
      calls.add(() -> instance.claim("same-event"));
    }
    try (var pool = Executors.newFixedThreadPool(4)) {
      int claimed = 0;
      for (var future : pool.invokeAll(calls)) if (future.get().isPresent()) claimed++;
      assertEquals(1, claimed);
    }
  }

  @Test void expiresAfter24HoursAndOldReleaseCannotDeleteNewClaim() {
    var source = database();
    var start = Instant.parse("2026-01-01T00:00:00Z");
    var one = new LarkEventDeduplicator(source, Clock.fixed(start, ZoneOffset.UTC));
    var old = one.claim("event-one").orElseThrow();
    var beforeExpiry = new LarkEventDeduplicator(source, Clock.fixed(start.plusSeconds(86399), ZoneOffset.UTC));
    assertTrue(beforeExpiry.claim("event-one").isEmpty());
    var afterExpiry = new LarkEventDeduplicator(source, Clock.fixed(start.plusSeconds(86400), ZoneOffset.UTC));
    assertTrue(afterExpiry.claim("event-one").isPresent());
    one.release(old);
    assertTrue(afterExpiry.claim("event-one").isEmpty());
  }

  @Test void releaseAllowsRetryAfterRejectedSubmission() {
    var dedupe = new LarkEventDeduplicator(database());
    var claim = dedupe.claim("event-one").orElseThrow();
    dedupe.release(claim);
    assertTrue(dedupe.claim("event-one").isPresent());
  }
}
