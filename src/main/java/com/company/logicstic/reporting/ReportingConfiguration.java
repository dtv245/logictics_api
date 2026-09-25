package com.company.logicstic.reporting;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the two reporting collaborators that cannot be constructed by component scanning.
 *
 * <p>{@link ReportPeriodResolver} takes its clock as a constructor argument rather than calling
 * {@code OffsetDateTime.now()} itself, which is what makes "the last twelve months" a fixed,
 * assertable value in a test. That argument has to come from somewhere, and this is that somewhere:
 * one clock bean in the whole application, so every window and every ageing instant on this
 * endpoint family is read from the same source. Two clocks would let a report be aged at one moment
 * and windowed at another, and the response echoes both, so the inconsistency would be visible on
 * the wire without being explicable.
 *
 * <p>This is also why the clock is a bean at all rather than a static call: a slice test that
 * replaces it can pin a response to a fixed date instead of asserting against whatever today
 * happens to be.
 */
@Configuration
@Profile("!nodb")
public class ReportingConfiguration {

  /**
   * UTC, matching {@code hibernate.jdbc.time_zone} and the {@code timestamptz} columns every window
   * is compared against. Reading "now" in the server's local zone would make a month boundary — and
   * therefore a monthly total — depend on where the process happens to be running.
   */
  @Bean
  Clock utcClock() {
    return Clock.systemUTC();
  }

  @Bean
  ReportPeriodResolver reportPeriodResolver(Clock utcClock) {
    return new ReportPeriodResolver(utcClock);
  }
}
