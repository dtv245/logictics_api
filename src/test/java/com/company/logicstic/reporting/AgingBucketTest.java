package com.company.logicstic.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins where an outstanding invoice lands in the ageing report.
 *
 * <p>The case that matters is the invoice with no due date. {@code invoices.due_date} is nullable,
 * and a row without one has not been shown to be current — nobody established when it falls due.
 * Folding those rows into {@code CURRENT} would let genuinely stale debt sit inside the healthiest
 * band on the report, which is the most consequential way an ageing report can mislead.
 */
class AgingBucketTest {

  private static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-06-15T00:00:00Z");

  @Test
  @DisplayName("buckets an invoice with no due date into its own band")
  void should_bucket_invoice_into_no_due_date_when_due_date_is_null() {
    assertThat(AgingBucket.of(null, AS_OF)).isEqualTo(AgingBucket.NO_DUE_DATE);
    // Stated as an inequality too: the failure being guarded against is not a wrong band, it is
    // this row being absorbed into CURRENT and disappearing from the overdue total.
    assertThat(AgingBucket.of(null, AS_OF)).isNotEqualTo(AgingBucket.CURRENT);
  }

  @Test
  @DisplayName("treats a due date in the future, or today, as current")
  void should_treat_a_due_date_in_the_future_as_current() {
    assertThat(AgingBucket.of(AS_OF.plusDays(1), AS_OF)).isEqualTo(AgingBucket.CURRENT);
    assertThat(AgingBucket.of(AS_OF, AS_OF)).isEqualTo(AgingBucket.CURRENT);
  }

  @Test
  @DisplayName("places each overdue age in its band")
  void should_place_each_overdue_age_in_its_band() {
    assertThat(AgingBucket.of(AS_OF.minusDays(1), AS_OF)).isEqualTo(AgingBucket.DAYS_1_30);
    assertThat(AgingBucket.of(AS_OF.minusDays(30), AS_OF)).isEqualTo(AgingBucket.DAYS_1_30);
    assertThat(AgingBucket.of(AS_OF.minusDays(31), AS_OF)).isEqualTo(AgingBucket.DAYS_31_60);
    assertThat(AgingBucket.of(AS_OF.minusDays(60), AS_OF)).isEqualTo(AgingBucket.DAYS_31_60);
    assertThat(AgingBucket.of(AS_OF.minusDays(61), AS_OF)).isEqualTo(AgingBucket.DAYS_61_90);
    assertThat(AgingBucket.of(AS_OF.minusDays(90), AS_OF)).isEqualTo(AgingBucket.DAYS_61_90);
    assertThat(AgingBucket.of(AS_OF.minusDays(91), AS_OF)).isEqualTo(AgingBucket.OVER_90);
    assertThat(AgingBucket.of(AS_OF.minusYears(3), AS_OF)).isEqualTo(AgingBucket.OVER_90);
  }

  @Test
  @DisplayName("leaves no gap between adjacent bands")
  void should_leave_no_gap_between_adjacent_bands() {
    // Every whole number of days overdue must land in exactly one band. A gap would drop an invoice
    // out of the ageing total entirely, which reads as debt that does not exist.
    for (int days = 0; days <= 200; days++) {
      AgingBucket bucket = AgingBucket.of(AS_OF.minusDays(days), AS_OF);
      assertThat(bucket).as("%d days overdue", days).isNotEqualTo(AgingBucket.NO_DUE_DATE);
    }
  }

  @Test
  @DisplayName("lists every band in report order, with no due date last")
  void should_list_every_band_in_report_order() {
    assertThat(AgingBucket.reportOrder()).containsExactly(AgingBucket.values());
    assertThat(AgingBucket.reportOrder()).endsWith(AgingBucket.NO_DUE_DATE);
    assertThat(AgingBucket.reportOrder()).startsWith(AgingBucket.CURRENT);
  }

  @Test
  @DisplayName("gives every band a stable id and a distinct frontend key")
  void should_give_every_band_a_stable_id_and_label_key() {
    assertThat(AgingBucket.reportOrder())
        .allSatisfy(
            bucket -> {
              assertThat(bucket.id()).isNotBlank();
              assertThat(bucket.labelKey()).startsWith("executive.aging.");
            });
    assertThat(AgingBucket.reportOrder()).extracting(AgingBucket::id).doesNotHaveDuplicates();
    assertThat(AgingBucket.reportOrder()).extracting(AgingBucket::labelKey).doesNotHaveDuplicates();
  }
}
