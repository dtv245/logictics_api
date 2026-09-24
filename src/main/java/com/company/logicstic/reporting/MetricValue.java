package com.company.logicstic.reporting;

import java.math.BigDecimal;

/**
 * A single reported number, together with whether it is actually known.
 *
 * <p>Every metric this feature returns is wrapped in this type. That is the central defence of the
 * reporting layer, and it exists because of one line in the client: the executive screen's metric
 * adapter builds each KPI as {@code value: current ?? 0}. A missing number on the wire is therefore
 * rendered as a confident {@code 0}. On a dashboard whose job is to tell a CEO whether the company
 * is healthy, a fabricated zero is the most dangerous output the API can produce — it reads as "no
 * cost", "no downtime", "no risk".
 *
 * <p>Wrapping the value makes that misreading impossible: an unavailable metric serialises as
 * {@code {"value":null,"available":false,"reasonCode":"NO_SOURCE_ROWS"}}, which no client can
 * mistake for a measurement.
 *
 * @param value the number, or {@code null} when {@link #available} is {@code false}
 * @param available whether {@link #value} is a real measurement
 * @param reasonCode why the metric is unavailable; {@code null} when it is available
 */
public record MetricValue(BigDecimal value, boolean available, String reasonCode) {

  /** A metric that was measured. */
  public static MetricValue of(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "A measured MetricValue cannot hold null; use unavailable(reason) instead");
    }
    return new MetricValue(value, true, null);
  }

  /** A metric that was measured, from a whole number. */
  public static MetricValue of(long value) {
    return of(BigDecimal.valueOf(value));
  }

  /** A metric that could not be computed, and why. */
  public static MetricValue unavailable(MetricUnavailableReason reason) {
    if (reason == null) {
      throw new IllegalArgumentException("An unavailable MetricValue must state a reason");
    }
    return new MetricValue(null, false, reason.name());
  }

  /**
   * A rate over a denominator, or {@link MetricUnavailableReason#ZERO_DENOMINATOR} when there is
   * nothing to divide by.
   *
   * <p>A zero denominator never yields zero. Revenue per mile with no miles driven is not "0 per
   * mile"; it is unknown, and reporting it as zero would drag an average down with a number nobody
   * measured.
   *
   * @param numerator the top of the fraction
   * @param denominator the bottom of the fraction; zero or negative means unknown
   * @param scale decimal places to round the quotient to
   */
  public static MetricValue ratio(BigDecimal numerator, BigDecimal denominator, int scale) {
    if (numerator == null || denominator == null || denominator.signum() <= 0) {
      return unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    }
    return of(numerator.divide(denominator, scale, java.math.RoundingMode.HALF_UP));
  }
}
