package com.company.logicstic.reporting;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Turns optional {@code from}/{@code to} request parameters into a bounded reporting window.
 *
 * <p>The clock is injected rather than read from {@link OffsetDateTime#now()} so that "the last
 * twelve months" is a fixed, testable statement instead of a value that changes between the test
 * run and the assertion.
 *
 * <p>The window is half-open — {@code [from, to)} — so a row at exactly midnight on the boundary
 * belongs to one period and not two. Adjacent periods therefore partition the timeline without
 * double-counting, which matters because the cost endpoint reports the preceding window alongside
 * the current one.
 */
public class ReportPeriodResolver {

  /** Months of history returned when the caller does not say. */
  public static final int DEFAULT_RANGE_MONTHS = 12;

  /**
   * Longest window a caller may ask for. Reporting scans aggregate without a covering index on some
   * of the predicates, so an unbounded range would let one request read the whole history of a
   * tenant. Bounded rather than merely defaulted, for the same reason pagination has a maximum.
   */
  public static final int MAX_RANGE_MONTHS = 60;

  private final Clock clock;

  public ReportPeriodResolver(Clock clock) {
    this.clock = clock;
  }

  /**
   * Resolves the requested window and the equal-length window immediately before it.
   *
   * @param from inclusive start, or {@code null} for {@code to} minus {@link #DEFAULT_RANGE_MONTHS}
   * @param to exclusive end, or {@code null} for now
   * @return the current and previous windows
   * @throws IllegalArgumentException if {@code from} is not before {@code to}, or the window is
   *     longer than {@link #MAX_RANGE_MONTHS}
   */
  public Period resolve(OffsetDateTime from, OffsetDateTime to) {
    OffsetDateTime end = to != null ? to : now();
    OffsetDateTime start = from != null ? from : end.minusMonths(DEFAULT_RANGE_MONTHS);

    if (!start.isBefore(end)) {
      throw new IllegalArgumentException("'from' must be earlier than 'to'");
    }
    if (start.isBefore(end.minusMonths(MAX_RANGE_MONTHS))) {
      throw new IllegalArgumentException(
          "'from' must be within " + MAX_RANGE_MONTHS + " months of 'to'");
    }

    OffsetDateTime previousStart = start.minusMonths(monthsBetween(start, end));
    return new Period(start, end, previousStart, start);
  }

  /** Resolves a window ending at {@code asOf}, defaulting to the last twelve months. */
  public Period resolveEndingAt(OffsetDateTime asOf) {
    return resolve(null, asOf);
  }

  /**
   * The current instant, in UTC, from the injected clock.
   *
   * <p>Exposed so an endpoint that measures a position rather than a flow — receivables is aged at
   * an instant, not over a window — reads "now" from the same clock the windows do. Two clocks
   * would let a report be aged at one moment and windowed at another.
   */
  public OffsetDateTime now() {
    return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
  }

  private static long monthsBetween(OffsetDateTime start, OffsetDateTime end) {
    long months = ChronoUnit.MONTHS.between(start.toLocalDate(), end.toLocalDate());
    return Math.max(months, 1);
  }

  /**
   * A reporting window and the window before it.
   *
   * @param from inclusive start of the current window
   * @param to exclusive end of the current window
   * @param previousFrom inclusive start of the preceding window, of equal length
   * @param previousTo exclusive end of the preceding window, equal to {@code from}
   */
  public record Period(
      OffsetDateTime from,
      OffsetDateTime to,
      OffsetDateTime previousFrom,
      OffsetDateTime previousTo) {

    /**
     * The window shifted back by one full period, for a caller that needs a period-over-period gap.
     */
    public Period previous() {
      OffsetDateTime previousStart =
          previousFrom.minusMonths(monthsBetween(previousFrom, previousTo));
      return new Period(previousFrom, previousTo, previousStart, previousFrom);
    }
  }
}
