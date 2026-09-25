package com.company.logicstic.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic rules of the reporting layer, tested without a database.
 *
 * <p>Every case here pins a way the executive screen could be handed a confident number that is not
 * a measurement. That is the failure this feature exists to avoid, so the tests are written as
 * statements about what must never be produced — a rate over a zero denominator, a spread collapsed
 * into one of its own terms, an index computed over a subset of the customers it describes.
 */
class ReportingMetricsCalculatorTest {

  // ── no rows is not zero rows ─────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns null rather than zero when there was nothing to sum")
  void should_return_null_instead_of_zero_for_an_empty_sum() {
    // "Total cost: 0" and "no costs recorded" call for opposite responses from whoever reads the
    // dashboard, so the total is absent rather than zero and the response can say which it is.
    assertThat(ReportingMetricsCalculator.sum(List.of())).isNull();
    assertThat(ReportingMetricsCalculator.sum(null)).isNull();
    assertThat(ReportingMetricsCalculator.sum(java.util.Arrays.asList(null, null))).isNull();
  }

  @Test
  @DisplayName("skips absent entries but still totals the ones that are present")
  void should_sum_the_present_entries_and_ignore_absent_ones() {
    assertThat(
            ReportingMetricsCalculator.sum(
                java.util.Arrays.asList(new BigDecimal("10.005"), null, new BigDecimal("20.00"))))
        .isEqualByComparingTo("30.01");
  }

  @Test
  @DisplayName("reports a count of zero as a real measurement")
  void should_report_a_zero_count_as_available() {
    // Unlike a sum: "no invoice is overdue" is a fact that was established, not a gap. Reporting it
    // as unavailable would hide the healthiest possible receivables position behind an error.
    MetricValue none = ReportingMetricsCalculator.count(0);

    assertThat(none.available()).isTrue();
    assertThat(none.value()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("marks a monetary total unavailable when there were no rows")
  void should_mark_a_missing_monetary_total_unavailable() {
    MetricValue total =
        ReportingMetricsCalculator.money(null, MetricUnavailableReason.NO_SOURCE_ROWS);

    assertThat(total.available()).isFalse();
    assertThat(total.reasonCode()).isEqualTo("NO_SOURCE_ROWS");
  }

  // ── rates over nothing ───────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("returns unavailable, never zero, for revenue per mile with no miles driven")
  void should_return_unavailable_revenue_per_mile_when_loaded_miles_is_zero() {
    MetricValue rate =
        ReportingMetricsCalculator.perMile(new BigDecimal("12000.00"), new BigDecimal("0"));

    assertThat(rate.available()).isFalse();
    assertThat(rate.reasonCode()).isEqualTo("ZERO_DENOMINATOR");
    // The assertion that matters: the value is absent, not 0. A zero here would render as "$0.00
    // per mile", which is a claim about cost that nobody measured.
    assertThat(rate.value()).isNull();
    assertThat(rate.value()).isNotEqualTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("treats a missing mile total and a missing numerator differently")
  void should_distinguish_a_missing_numerator_from_a_missing_denominator() {
    // No cost rows at all is a data-entry problem; cost rows with no distance to divide by is a
    // measurement problem. Collapsing them into one reason would send the reader to the wrong fix.
    assertThat(ReportingMetricsCalculator.perMile(null, new BigDecimal("1000")).reasonCode())
        .isEqualTo("NO_SOURCE_ROWS");
    assertThat(ReportingMetricsCalculator.perMile(new BigDecimal("100"), null).reasonCode())
        .isEqualTo("ZERO_DENOMINATOR");
    assertThat(
            ReportingMetricsCalculator.perMile(new BigDecimal("100"), new BigDecimal("-5"))
                .reasonCode())
        .isEqualTo("ZERO_DENOMINATOR");
  }

  @Test
  @DisplayName("divides when there is a real denominator")
  void should_divide_by_a_positive_denominator() {
    assertThat(
            ReportingMetricsCalculator.perMile(new BigDecimal("2500.00"), new BigDecimal("1000")))
        .extracting(MetricValue::value)
        .isEqualTo(new BigDecimal("2.5000"));
  }

  @Test
  @DisplayName("keeps a spread unavailable when one of its terms is unknown")
  void should_return_unavailable_contribution_spread_when_cost_is_unavailable() {
    MetricValue revenuePerMile = MetricValue.of(new BigDecimal("2.5000"));
    MetricValue costPerMile = MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);

    MetricValue spread = ReportingMetricsCalculator.difference(revenuePerMile, costPerMile);

    assertThat(spread.available()).isFalse();
    assertThat(spread.reasonCode()).isEqualTo("NO_SOURCE_ROWS");
    // The tempting shorthand is to return the known side, which reports the spread as equal to
    // revenue per mile — a contribution margin of 100% for a month whose costs are simply unknown.
    // That turns an unprofitable period into a profitable-looking one, so it is asserted against.
    assertThat(spread.value()).isNull();
    assertThat(spread).isNotEqualTo(revenuePerMile);
  }

  @Test
  @DisplayName("subtracts when both terms are known")
  void should_subtract_two_known_rates() {
    assertThat(
            ReportingMetricsCalculator.difference(
                MetricValue.of(new BigDecimal("2.5000")), MetricValue.of(new BigDecimal("1.7500"))))
        .extracting(MetricValue::value)
        .isEqualTo(new BigDecimal("0.7500"));
  }

  @Test
  @DisplayName("returns the missing side's own reason for an unavailable difference")
  void should_report_the_reason_of_whichever_side_is_missing() {
    MetricValue present = MetricValue.of(new BigDecimal("2.5000"));
    MetricValue noAllocation = MetricValue.unavailable(MetricUnavailableReason.NO_COST_ALLOCATION);

    assertThat(ReportingMetricsCalculator.difference(present, noAllocation).reasonCode())
        .isEqualTo("NO_COST_ALLOCATION");
    assertThat(ReportingMetricsCalculator.difference(noAllocation, present).reasonCode())
        .isEqualTo("NO_COST_ALLOCATION");
  }

  @Test
  @DisplayName("subtracts money at the scale money is stored")
  void should_subtract_two_known_amounts_at_money_scale() {
    MetricValue profit =
        ReportingMetricsCalculator.moneyDifference(
            MetricValue.of(new BigDecimal("1000.00")), MetricValue.of(new BigDecimal("250.50")));

    assertThat(profit.available()).isTrue();
    assertThat(profit.value()).isEqualByComparingTo("749.50");
    // Two decimal places, not four: this is a monetary total, and a profit printed as 749.5000
    // invites a reader to trust digits the source column never held.
    assertThat(profit.value().scale()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "keeps a missing term's reason for a monetary difference, and never returns the known side")
  void should_keep_the_missing_reason_for_a_monetary_difference() {
    MetricValue revenue = MetricValue.of(new BigDecimal("1000.00"));
    MetricValue cost = MetricValue.unavailable(MetricUnavailableReason.NO_SOURCE_ROWS);

    MetricValue profit = ReportingMetricsCalculator.moneyDifference(revenue, cost);

    assertThat(profit.available()).isFalse();
    assertThat(profit.value()).isNull();
    assertThat(profit.reasonCode()).isEqualTo("NO_SOURCE_ROWS");
    // An operating profit that equals revenue is the single most flattering wrong number this
    // endpoint could produce, and it is exactly what "cost is unknown, so subtract nothing" yields.
    assertThat(profit).isNotEqualTo(revenue);
  }

  @Test
  @DisplayName(
      "reports a negative monetary difference as available, because a loss is a measurement")
  void should_report_a_negative_monetary_difference_as_available() {
    MetricValue loss =
        ReportingMetricsCalculator.moneyDifference(
            MetricValue.of(new BigDecimal("100.00")), MetricValue.of(new BigDecimal("250.00")));

    assertThat(loss.available()).isTrue();
    assertThat(loss.value()).isEqualByComparingTo("-150.00");
  }

  @Test
  @DisplayName("expresses a part of a whole as a percentage")
  void should_compute_a_share_as_a_percentage() {
    MetricValue share =
        ReportingMetricsCalculator.share(new BigDecimal("30.00"), new BigDecimal("120.00"));

    assertThat(share.available()).isTrue();
    // A percentage on a 0–100 scale, because that is the scale the concentration thresholds and the
    // cost chart are written against; a 0–1 fraction would read as 0.25% and never trip a
    // threshold.
    assertThat(share.value()).isEqualByComparingTo("25.00");
  }

  @Test
  @DisplayName("returns unavailable rather than zero for a share of nothing")
  void should_return_unavailable_for_a_share_of_a_zero_whole() {
    MetricValue share = ReportingMetricsCalculator.share(BigDecimal.ZERO, BigDecimal.ZERO);

    assertThat(share.available()).isFalse();
    assertThat(share.value()).isNull();
    assertThat(share.reasonCode()).isEqualTo("ZERO_DENOMINATOR");
  }

  @Test
  @DisplayName("reports a zero part of a real whole as an available zero")
  void should_report_a_zero_part_of_a_real_whole_as_available() {
    // A category the business does not spend on really does hold 0% of cost, and that zero was
    // measured. Only the denominator is tested for sign; testing the numerator too would turn an
    // unused category into a missing feed.
    MetricValue share = ReportingMetricsCalculator.share(BigDecimal.ZERO, new BigDecimal("500.00"));

    assertThat(share.available()).isTrue();
    assertThat(share.value()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("returns unavailable rather than zero for a percentage of nothing")
  void should_return_unavailable_for_a_percentage_over_a_zero_denominator() {
    MetricValue share =
        ReportingMetricsCalculator.percentage(0, 0, MetricUnavailableReason.NO_SOURCE_ROWS);

    assertThat(share.available()).isFalse();
    assertThat(share.value()).isNull();
  }

  @Test
  @DisplayName("computes a percentage of a real denominator")
  void should_compute_a_percentage() {
    assertThat(ReportingMetricsCalculator.percentage(1, 3, MetricUnavailableReason.NO_SOURCE_ROWS))
        .extracting(MetricValue::value)
        .isEqualTo(new BigDecimal("33.33"));
    assertThat(ReportingMetricsCalculator.percentage(0, 4, MetricUnavailableReason.NO_SOURCE_ROWS))
        .extracting(MetricValue::value)
        .isEqualTo(new BigDecimal("0.00"));
  }

  // ── concentration ────────────────────────────────────────────────────────────────────────────

  /**
   * Thirty customers: one dominant account and twenty-nine small ones.
   *
   * <p>Chosen so the true index is exact. Total revenue is 100, so the leader's share is 71% and
   * every other customer's is 1%, giving {@code 71² + 29 × 1² = 5070}. Any index that is not 5070
   * was computed over the wrong set of customers.
   */
  private static List<BigDecimal> thirtyCustomers() {
    List<BigDecimal> revenues = new ArrayList<>();
    revenues.add(new BigDecimal("71"));
    for (int index = 0; index < 29; index++) {
      revenues.add(BigDecimal.ONE);
    }
    return revenues;
  }

  @Test
  @DisplayName("computes HHI over every customer, not only the rows a page returned")
  void should_compute_hhi_over_all_customers_not_only_returned_rows() {
    MetricValue index = ReportingMetricsCalculator.herfindahlIndex(thirtyCustomers());

    // 71² + 29 × 1² = 5070.
    assertThat(index.value()).isEqualByComparingTo("5070.00");
  }

  @Test
  @DisplayName(
      "computes HHI over more than 100 customers verifying complete-population calculation")
  void should_compute_hhi_over_more_than_100_customers() {
    List<BigDecimal> revenues = new ArrayList<>();
    // 1 dominant customer with 50.00, and 100 small customers with 0.50 each (Total = 100.00)
    revenues.add(new BigDecimal("50.00"));
    for (int i = 0; i < 100; i++) {
      revenues.add(new BigDecimal("0.50"));
    }
    // Total = 100.00.
    // dominant share = 50.00%, small share = 0.50% each.
    // HHI = 50.00^2 + 100 * (0.50^2) = 2500.00 + 100 * 0.25 = 2500.00 + 25.00 = 2525.00
    MetricValue index = ReportingMetricsCalculator.herfindahlIndex(revenues);
    assertThat(index.available()).isTrue();
    assertThat(index.value()).isEqualByComparingTo("2525.00");
  }

  @Test
  @DisplayName(
      "inflates the index when the customer list is truncated, so the whole list is required")
  void should_inflate_the_index_when_the_customer_list_is_truncated() {
    List<BigDecimal> all = thirtyCustomers();
    List<BigDecimal> topTen = all.subList(0, 10);

    MetricValue complete = ReportingMetricsCalculator.herfindahlIndex(all);
    MetricValue truncated = ReportingMetricsCalculator.herfindahlIndex(topTen);

    assertThat(complete.value()).isEqualByComparingTo("5070.00");
    // The ten returned rows hold 80 of the 100 in revenue, and the index is then measured against
    // that 80: 88.75² + 9 × 1.25² = 7890.625. Truncation is neither a rounding of the true index
    // nor
    // a conservative one — dropping the smallest customers shrinks the total faster than the
    // numerators, so every retained share grows and the index reads higher than the truth. A client
    // recomputing it from the rows it was handed would report concentration as worse than it is,
    // which is why the service sends the computed figure rather than the inputs to it.
    assertThat(truncated.value()).isEqualByComparingTo("7890.63");
    assertThat(truncated.value()).isGreaterThan(complete.value());
  }

  @Test
  @DisplayName("skips a negative revenue rather than adding it back as a positive term")
  void should_ignore_negative_revenue_in_the_index() {
    List<BigDecimal> withCredit = new ArrayList<>(thirtyCustomers());
    withCredit.add(new BigDecimal("-40"));

    // A credit balance is not a share of anything. Squaring it would add a positive term to the
    // index and shrink the denominator at once, moving the number in a direction with no
    // interpretation. The index over the positive revenue is the same as without the credit row.
    assertThat(ReportingMetricsCalculator.herfindahlIndex(withCredit).value())
        .isEqualByComparingTo("5070.00");
  }

  @Test
  @DisplayName("returns unavailable for an index with no positive revenue to measure")
  void should_return_unavailable_for_an_index_over_nothing() {
    assertThat(ReportingMetricsCalculator.herfindahlIndex(List.of()).reasonCode())
        .isEqualTo("NO_SOURCE_ROWS");
    assertThat(
            ReportingMetricsCalculator.herfindahlIndex(List.of(new BigDecimal("-1"))).reasonCode())
        .isEqualTo("ZERO_DENOMINATOR");
  }

  @Test
  @DisplayName("takes the top-N share against the full total")
  void should_take_the_top_n_share_of_the_whole_customer_list() {
    List<BigDecimal> revenues = thirtyCustomers();

    assertThat(ReportingMetricsCalculator.topNShare(revenues, 1).value())
        .isEqualByComparingTo("71.00");
    assertThat(ReportingMetricsCalculator.topNShare(revenues, 3).value())
        .isEqualByComparingTo("73.00");
  }

  @Test
  @DisplayName("returns unavailable for a top-N share with no revenue to divide")
  void should_return_unavailable_for_a_top_n_share_over_nothing() {
    assertThat(ReportingMetricsCalculator.topNShare(List.of(), 1).reasonCode())
        .isEqualTo("NO_SOURCE_ROWS");
    assertThat(ReportingMetricsCalculator.topNShare(List.of(BigDecimal.ZERO), 1).reasonCode())
        .isEqualTo("ZERO_DENOMINATOR");
  }

  @Test
  @DisplayName("orders a copy and leaves the caller's list untouched")
  void should_order_a_copy_without_mutating_the_input() {
    List<BigDecimal> original =
        new ArrayList<>(
            java.util.Arrays.asList(new BigDecimal("3"), new BigDecimal("1"), new BigDecimal("2")));

    List<BigDecimal> descending = ReportingMetricsCalculator.descending(original);

    assertThat(descending)
        .containsExactly(new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("1"));
    // The service hands the same list to the index and to the share calculation. Sorting in place
    // would be a silent reordering of a caller's data, which no assertion on the result would show.
    assertThat(original)
        .containsExactly(new BigDecimal("3"), new BigDecimal("1"), new BigDecimal("2"));
  }

  // ── the wire contract ────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("never reports an unavailable metric as a number, zero included")
  void should_never_report_an_unavailable_metric_as_a_number() {
    // Every way this class can produce an unavailable metric, gathered in one place. The client's
    // adapter reads `value`, so an unavailable metric that carried 0 instead of null would render
    // as a measurement on the executive screen.
    List<MetricValue> unavailable =
        List.of(
            ReportingMetricsCalculator.money(null, MetricUnavailableReason.NO_SOURCE_ROWS),
            ReportingMetricsCalculator.percentage(0, 0, MetricUnavailableReason.NO_SOURCE_ROWS),
            ReportingMetricsCalculator.perMile(BigDecimal.TEN, BigDecimal.ZERO),
            ReportingMetricsCalculator.perMile(null, BigDecimal.TEN),
            ReportingMetricsCalculator.difference(
                MetricValue.unavailable(MetricUnavailableReason.NO_DOWNTIME_INTERVALS),
                MetricValue.of(BigDecimal.TEN)),
            ReportingMetricsCalculator.topNShare(List.of(), 3),
            ReportingMetricsCalculator.herfindahlIndex(List.of()),
            MetricValue.ratio(BigDecimal.TEN, BigDecimal.ZERO, 2));

    assertThat(unavailable)
        .allSatisfy(
            metric -> {
              assertThat(metric.available()).isFalse();
              assertThat(metric.value()).isNull();
              assertThat(metric.reasonCode()).isNotBlank();
            });
  }

  @Test
  @DisplayName("refuses to build a measured metric with no value")
  void should_refuse_a_measured_metric_without_a_value() {
    // The one construction that would defeat the whole contract: available == true with a null
    // value renders as zero through the client's `?? 0`.
    assertThatThrownBy(() -> MetricValue.of((BigDecimal) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("refuses to build an unavailable metric without a reason")
  void should_refuse_an_unavailable_metric_without_a_reason() {
    assertThatThrownBy(() -> MetricValue.unavailable(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── period keys ──────────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("buckets a timestamp into its UTC month")
  void should_bucket_a_timestamp_into_its_utc_month() {
    // 00:30 on the first of January in a +07:00 zone is still December in UTC. Monthly buckets must
    // not move because the server's zone changed, or a month boundary would shift with a deployment
    // setting that has nothing to do with the data.
    assertThat(
            ReportingMetricsCalculator.monthKey(OffsetDateTime.parse("2026-01-01T00:30:00+07:00")))
        .isEqualTo("2025-12");
    assertThat(ReportingMetricsCalculator.monthKey(OffsetDateTime.parse("2026-01-15T00:00:00Z")))
        .isEqualTo("2026-01");
  }
}
