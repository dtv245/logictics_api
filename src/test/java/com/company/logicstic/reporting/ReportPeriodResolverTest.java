package com.company.logicstic.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the reporting window: its default length, its bounds, and its half-open shape.
 *
 * <p>The clock is injected, which is what makes the default window assertable at all. A resolver
 * that read {@link OffsetDateTime#now()} would give a window that moved between the call and the
 * assertion, and the only test anyone could write would be a range check on a value the test itself
 * could not predict.
 */
class ReportPeriodResolverTest {

  /**
   * A fixed instant so "the last twelve months" is a constant of this class, not of the calendar.
   */
  private static final Instant NOW = Instant.parse("2026-06-15T09:30:00Z");

  private static final ReportPeriodResolver RESOLVER =
      new ReportPeriodResolver(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName("defaults to the twelve months ending now")
  void should_default_to_the_twelve_months_ending_now() {
    ReportPeriodResolver.Period period = RESOLVER.resolve(null, null);

    assertThat(period.to()).isEqualTo(OffsetDateTime.parse("2026-06-15T09:30:00Z"));
    assertThat(period.from()).isEqualTo(OffsetDateTime.parse("2025-06-15T09:30:00Z"));
  }

  @Test
  @DisplayName("reads the window from the injected clock, not from the system clock")
  void should_read_the_window_from_the_injected_clock() {
    // The whole reason the Clock is a constructor argument. Two resolvers built at the same moment
    // with different clocks must disagree, or the default window is not testable anywhere.
    ReportPeriodResolver other =
        new ReportPeriodResolver(
            Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(other.resolve(null, null).to()).isNotEqualTo(RESOLVER.resolve(null, null).to());
  }

  @Test
  @DisplayName("converts the clock's instant to UTC")
  void should_convert_the_window_to_utc() {
    // A server in +07:00 must not produce a window whose end is stated in a zone the database
    // comparison then reinterprets. The columns are timestamptz and the aggregates bucket in UTC.
    ReportPeriodResolver bangkok =
        new ReportPeriodResolver(Clock.fixed(NOW, ZoneId.of("Asia/Bangkok")));

    ReportPeriodResolver.Period period = bangkok.resolve(null, null);

    assertThat(period.to().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(period.to()).isEqualTo(OffsetDateTime.parse("2026-06-15T09:30:00Z"));
  }

  @Test
  @DisplayName("honours explicit bounds")
  void should_honour_explicit_bounds() {
    OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-04-01T00:00:00Z");

    ReportPeriodResolver.Period period = RESOLVER.resolve(from, to);

    assertThat(period.from()).isEqualTo(from);
    assertThat(period.to()).isEqualTo(to);
  }

  @Test
  @DisplayName("places the previous window immediately before the current one")
  void should_place_the_previous_window_immediately_before_the_current_one() {
    ReportPeriodResolver.Period period =
        RESOLVER.resolve(
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-04-01T00:00:00Z"));

    // previousTo == from is the property that makes the two windows non-overlapping. If they shared
    // a boundary row, a period-over-period comparison would count it in both.
    assertThat(period.previousTo()).isEqualTo(period.from());
    assertThat(period.previousFrom()).isEqualTo(OffsetDateTime.parse("2025-10-01T00:00:00Z"));
  }

  @Test
  @DisplayName("shifts a period back by its own length")
  void should_shift_a_period_back_by_its_own_length() {
    ReportPeriodResolver.Period period =
        RESOLVER.resolve(
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-04-01T00:00:00Z"));

    ReportPeriodResolver.Period earlier = period.previous();

    // previous() slides the window itself back one period, and carries the window before that
    // alongside it. So the period's own previousFrom/previousTo become the new from/to.
    assertThat(earlier.from()).isEqualTo(OffsetDateTime.parse("2025-10-01T00:00:00Z"));
    assertThat(earlier.to()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    assertThat(earlier.previousFrom()).isEqualTo(OffsetDateTime.parse("2025-07-01T00:00:00Z"));
    assertThat(earlier.previousTo()).isEqualTo(earlier.from());
  }

  @Test
  @DisplayName("shifts the previous window back by calendar months, not by the current day count")
  void should_shift_the_previous_window_back_by_calendar_months() {
    // Jan–Mar is 90 days; the three calendar months before it, Oct–Dec, are 92. The resolver shifts
    // by months, so the previous window is the previous *calendar* period rather than a span of the
    // same day count. That is the convention monthly reporting uses, and it is pinned here so the
    // difference is a stated property: a caller assuming equal day counts would compute a
    // period-over-period change across two windows of different length without noticing.
    ReportPeriodResolver.Period period =
        RESOLVER.resolve(
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-04-01T00:00:00Z"));

    assertThat(period.previousFrom()).isEqualTo(OffsetDateTime.parse("2025-10-01T00:00:00Z"));
    assertThat(period.previousTo()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    assertThat(period.previousFrom().getDayOfMonth()).isEqualTo(period.from().getDayOfMonth());
  }

  @Test
  @DisplayName("reports the injected clock's instant in UTC, so ageing and windowing share a now")
  void should_report_the_injected_clock_instant_in_utc() {
    // Receivables is aged at an instant while the financials are windowed over a range, and both
    // must read the same clock. If they did not, a report could be aged at one moment and windowed
    // at another, and the two figures beside each other would describe different times.
    assertThat(RESOLVER.now()).isEqualTo(OffsetDateTime.parse("2026-06-15T09:30:00Z"));

    ReportPeriodResolver bangkok =
        new ReportPeriodResolver(Clock.fixed(NOW, ZoneId.of("Asia/Bangkok")));
    OffsetDateTime fromBangkok = bangkok.now();

    assertThat(fromBangkok).isEqualTo(OffsetDateTime.parse("2026-06-15T09:30:00Z"));
    // The offset is normalised, not merely the instant: a client parsing this must not have to know
    // where the server ran to know which calendar day it was.
    assertThat(fromBangkok.getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  @DisplayName("resolves a window ending at a given instant")
  void should_resolve_a_window_ending_at_a_given_instant() {
    ReportPeriodResolver.Period period =
        RESOLVER.resolveEndingAt(OffsetDateTime.parse("2026-03-01T00:00:00Z"));

    assertThat(period.to()).isEqualTo(OffsetDateTime.parse("2026-03-01T00:00:00Z"));
    assertThat(period.from()).isEqualTo(OffsetDateTime.parse("2025-03-01T00:00:00Z"));
  }

  @Test
  @DisplayName("rejects a window that ends before it starts")
  void should_reject_an_inverted_window() {
    OffsetDateTime from = OffsetDateTime.parse("2026-04-01T00:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    assertThatThrownBy(() -> RESOLVER.resolve(from, to))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("earlier");
  }

  @Test
  @DisplayName("rejects an empty window")
  void should_reject_an_empty_window() {
    OffsetDateTime instant = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    // An empty window is not an empty report, it is a request that was misread. Returning zero rows
    // would look like a data problem on a screen that has no way to tell the two apart.
    assertThatThrownBy(() -> RESOLVER.resolve(instant, instant))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("accepts a window of exactly the maximum length")
  void should_accept_a_window_of_exactly_the_maximum_length() {
    OffsetDateTime to = OffsetDateTime.parse("2026-06-15T00:00:00Z");

    ReportPeriodResolver.Period period =
        RESOLVER.resolve(to.minusMonths(ReportPeriodResolver.MAX_RANGE_MONTHS), to);

    assertThat(period.from()).isEqualTo(OffsetDateTime.parse("2021-06-15T00:00:00Z"));
  }

  @Test
  @DisplayName("rejects a window one day longer than the maximum")
  void should_reject_a_window_longer_than_the_maximum() {
    OffsetDateTime to = OffsetDateTime.parse("2026-06-15T00:00:00Z");
    OffsetDateTime tooEarly = to.minusMonths(ReportPeriodResolver.MAX_RANGE_MONTHS).minusDays(1);

    // The bound exists because these aggregates scan without a covering index on every predicate,
    // so an unbounded range would let one request read a tenant's entire history. It is refused
    // rather than clamped: clamping would answer a different question than the one asked.
    assertThatThrownBy(() -> RESOLVER.resolve(tooEarly, to))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("60 months");
  }
}
