package com.company.logicstic.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The arithmetic behind the reporting endpoints, with no Spring, no repository and no clock.
 *
 * <p>Everything here is a pure function of its arguments, which is what makes the rules that matter
 * testable without a database: that HHI is computed over the whole customer set rather than the
 * rows that happen to be returned, that a rate over a zero denominator is unknown rather than zero,
 * and that a spread does not quietly collapse into one of its own terms when the other is missing.
 *
 * <p>The class is stateless and never instantiated.
 */
public final class ReportingMetricsCalculator {

  /** Scale for monetary aggregates, matching the {@code numeric(18,2)} columns they come from. */
  private static final int MONEY_SCALE = 2;

  /**
   * Scale for rates and percentages: enough to distinguish 2.58 from 2.6 without false precision.
   */
  private static final int RATE_SCALE = 4;

  private static final int PERCENT_SCALE = 2;

  /** HHI is conventionally quoted on a 0–10,000 scale, i.e. percentage shares squared. */
  private static final BigDecimal HHI_SCALE = new BigDecimal("100");

  private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

  private ReportingMetricsCalculator() {
    // static utility
  }

  // ── Aggregation primitives ──────────────────────────────────────────────

  /**
   * Sums amounts, treating {@code null} entries as absent rather than as zero.
   *
   * @return the total, or {@code null} when nothing was supplied. A caller distinguishes "no rows"
   *     from "rows totalling zero" by this, which is the whole reason it is not simply {@code
   *     BigDecimal.ZERO}
   */
  public static BigDecimal sum(List<BigDecimal> amounts) {
    if (amounts == null || amounts.isEmpty()) {
      return null;
    }
    BigDecimal total = BigDecimal.ZERO;
    boolean sawAny = false;
    for (BigDecimal amount : amounts) {
      if (amount != null) {
        total = total.add(amount);
        sawAny = true;
      }
    }
    return sawAny ? total.setScale(MONEY_SCALE, RoundingMode.HALF_UP) : null;
  }

  /**
   * A count as a metric. Unlike a sum, zero rows is a real measurement — "no invoices were overdue"
   * is a fact — so this is always available.
   */
  public static MetricValue count(long value) {
    return MetricValue.of(value);
  }

  /** A monetary total, or unavailable when there were no rows to total. */
  public static MetricValue money(BigDecimal total, MetricUnavailableReason whenAbsent) {
    return total == null ? MetricValue.unavailable(whenAbsent) : MetricValue.of(total);
  }

  /**
   * A percentage of a denominator.
   *
   * @param numerator the counted part
   * @param denominator the whole; zero or negative yields unavailable, never zero
   * @param reasonWhenUndefined the reason to report for a non-positive denominator
   */
  public static MetricValue percentage(
      long numerator, long denominator, MetricUnavailableReason reasonWhenUndefined) {
    if (denominator <= 0) {
      return MetricValue.unavailable(reasonWhenUndefined);
    }
    return MetricValue.of(
        BigDecimal.valueOf(numerator)
            .multiply(HHI_SCALE)
            .divide(BigDecimal.valueOf(denominator), PERCENT_SCALE, RoundingMode.HALF_UP));
  }

  /** A monetary rate per mile, or unavailable when no miles were driven. */
  public static MetricValue perMile(BigDecimal amount, BigDecimal miles) {
    if (amount == null) {
      return MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
    }
    if (miles == null || miles.signum() <= 0) {
      return MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    }
    return MetricValue.of(amount.divide(miles, RATE_SCALE, RoundingMode.HALF_UP));
  }

  /** A rate per 100,000 miles, or unavailable when no miles were driven. */
  public static MetricValue perHundredThousand(long count, BigDecimal miles) {
    if (miles == null || miles.signum() <= 0) {
      return MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    }
    return MetricValue.of(
        BigDecimal.valueOf(count)
            .multiply(BigDecimal.valueOf(100_000))
            .divide(miles, PERCENT_SCALE, RoundingMode.HALF_UP));
  }

  /**
   * The difference between two rates.
   *
   * <p>If either side is unknown the difference is unknown. Returning the known side instead — the
   * tempting shorthand when cost is missing — would report the spread as equal to revenue per mile
   * and make an unprofitable month look like a profitable one.
   */
  public static MetricValue difference(MetricValue left, MetricValue right) {
    return differenceAt(left, right, RATE_SCALE);
  }

  /** The difference between two monetary amounts, at the scale money is stored. */
  public static MetricValue moneyDifference(MetricValue left, MetricValue right) {
    return differenceAt(left, right, MONEY_SCALE);
  }

  private static MetricValue differenceAt(MetricValue left, MetricValue right, int scale) {
    if (!left.available() || !right.available()) {
      MetricUnavailableReason reason =
          !left.available()
              ? MetricUnavailableReason.valueOf(left.reasonCode())
              : MetricUnavailableReason.valueOf(right.reasonCode());
      return MetricValue.unavailable(reason);
    }
    return MetricValue.of(
        left.value().subtract(right.value()).setScale(scale, RoundingMode.HALF_UP));
  }

  /**
   * A part's share of a whole, as a percentage.
   *
   * <p>A whole of zero yields unavailable rather than zero: "no share of nothing" is not "0%", and
   * a chart that drew a zero-length bar would be asserting a measurement that was never taken. A
   * part of zero against a real whole is a genuine 0%, which is why the numerator is not tested.
   */
  public static MetricValue share(BigDecimal part, BigDecimal whole) {
    if (part == null || whole == null) {
      return MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
    }
    if (whole.signum() <= 0) {
      return MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    }
    return MetricValue.of(
        part.multiply(HHI_SCALE).divide(whole, PERCENT_SCALE, RoundingMode.HALF_UP));
  }

  // ── Concentration ───────────────────────────────────────────────────────

  /**
   * Combined share of the {@code topN} largest revenues, as a percentage of the total.
   *
   * <p>Operates on the complete list of every customer's revenue, not on a page of results. The
   * denominator is the total of the list it is given, so a truncated list answers a different
   * question: the top three customers' share of the ten that were returned, not of the business.
   * That figure is too high — the dropped customers leave the numerators untouched and shrink the
   * total — and a concentration report that overstates its own subject is no more usable than one
   * that understates it.
   *
   * @param revenues every customer's revenue, in any order
   * @param topN how many of the largest to combine
   */
  public static MetricValue topNShare(List<BigDecimal> revenues, int topN) {
    BigDecimal total = sum(revenues);
    if (total == null) {
      return MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
    }
    if (total.signum() <= 0) {
      return MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    }
    List<BigDecimal> descending = descending(revenues);
    BigDecimal top = BigDecimal.ZERO;
    for (int index = 0; index < Math.min(topN, descending.size()); index++) {
      top = top.add(descending.get(index));
    }
    return MetricValue.of(
        top.multiply(HHI_SCALE).divide(total, PERCENT_SCALE, RoundingMode.HALF_UP));
  }

  /**
   * The Herfindahl-Hirschman index over every customer's revenue, on a 0–10,000 scale.
   *
   * <p>Computed here, over the complete set, because it is the one figure on the executive screen
   * that a client cannot safely recompute: HHI is the sum of squared market shares, so omitting
   * customers always lowers it. A partial computation does not approximate the true index — it
   * flatters it.
   *
   * <p>Negative revenues are skipped rather than squared: a credit balance is not a share of
   * anything, and including it would add a positive term to the index while also shrinking the
   * denominator.
   *
   * @param revenues every customer's revenue
   * @return the index, or unavailable when there is no positive revenue to measure
   */
  public static MetricValue herfindahlIndex(List<BigDecimal> revenues) {
    if (revenues == null || revenues.isEmpty()) {
      return MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);
    }
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal revenue : revenues) {
      if (revenue != null && revenue.signum() > 0) {
        total = total.add(revenue);
      }
    }
    if (total.signum() <= 0) {
      return MetricValue.unavailable(MetricUnavailableReason.ZERO_DENOMINATOR);
    }
    BigDecimal index = BigDecimal.ZERO;
    for (BigDecimal revenue : revenues) {
      if (revenue == null || revenue.signum() <= 0) {
        continue;
      }
      BigDecimal sharePercent = revenue.multiply(HHI_SCALE).divide(total, 8, RoundingMode.HALF_UP);
      index = index.add(sharePercent.multiply(sharePercent));
    }
    return MetricValue.of(index.setScale(2, RoundingMode.HALF_UP));
  }

  /** Revenues ordered largest first; a stable copy, so callers' lists are never mutated. */
  public static List<BigDecimal> descending(List<BigDecimal> revenues) {
    List<BigDecimal> copy = new ArrayList<>();
    if (revenues != null) {
      for (BigDecimal revenue : revenues) {
        if (revenue != null) {
          copy.add(revenue);
        }
      }
    }
    copy.sort(Comparator.reverseOrder());
    return copy;
  }

  // ── Periods ─────────────────────────────────────────────────────────────

  /**
   * The {@code yyyy-MM} key a timestamp belongs to, in UTC.
   *
   * <p>UTC rather than a local zone: monthly buckets must not shift because the server's zone
   * changed, and the database stores {@code timestamptz}.
   */
  public static String monthKey(OffsetDateTime moment) {
    return YearMonth.from(moment.withOffsetSameInstant(ZoneOffset.UTC)).format(MONTH_KEY);
  }
}
