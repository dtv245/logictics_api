package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ageing bands for outstanding receivables.
 *
 * <p>{@link #NO_DUE_DATE} is a band of its own and is <strong>never</strong> folded into {@link
 * #CURRENT}. {@code invoices.due_date} is nullable, and a row without one has not been shown to be
 * current — nobody has established when it falls due. Sweeping those rows into "current" would let
 * genuinely stale debt hide inside the healthiest-looking band on the report, which is the single
 * most consequential way an ageing report can lie.
 */
public enum AgingBucket {
  /** Due in the future, or due today. */
  CURRENT("current", "executive.aging.current", 0, 0),
  /** Overdue by 1 to 30 days. */
  DAYS_1_30("days1To30", "executive.aging.days1To30", 1, 30),
  /** Overdue by 31 to 60 days. */
  DAYS_31_60("days31To60", "executive.aging.days31To60", 31, 60),
  /** Overdue by 61 to 90 days. */
  DAYS_61_90("days61To90", "executive.aging.days61To90", 61, 90),
  /** Overdue by more than 90 days. */
  OVER_90("over90", "executive.aging.over90", 91, Integer.MAX_VALUE),
  /** No due date on record. Not evidence of being current — evidence of not knowing. */
  NO_DUE_DATE("noDueDate", "executive.aging.noDueDate", -1, -1);

  private final String id;
  private final String labelKey;
  private final int minDaysOverdue;
  private final int maxDaysOverdue;

  AgingBucket(String id, String labelKey, int minDaysOverdue, int maxDaysOverdue) {
    this.id = id;
    this.labelKey = labelKey;
    this.minDaysOverdue = minDaysOverdue;
    this.maxDaysOverdue = maxDaysOverdue;
  }

  /** Stable machine identifier, used as the chart's series key. */
  public String id() {
    return id;
  }

  /** Frontend i18n key for the band's display name. */
  public String labelKey() {
    return labelKey;
  }

  /**
   * Places an invoice in a band.
   *
   * @param dueDate the invoice's due date, or {@code null} when none is recorded
   * @param asOf the instant the report is drawn at
   * @return {@link #NO_DUE_DATE} when {@code dueDate} is null, else the band the overdue age falls
   *     in
   */
  public static AgingBucket of(OffsetDateTime dueDate, OffsetDateTime asOf) {
    if (dueDate == null) {
      return NO_DUE_DATE;
    }
    long daysOverdue = java.time.Duration.between(dueDate, asOf).toDays();
    if (daysOverdue <= 0) {
      return CURRENT;
    }
    for (AgingBucket bucket : values()) {
      if (bucket == NO_DUE_DATE || bucket == CURRENT) {
        continue;
      }
      if (daysOverdue >= bucket.minDaysOverdue && daysOverdue <= bucket.maxDaysOverdue) {
        return bucket;
      }
    }
    return OVER_90;
  }

  /**
   * Every band in report order, so the ageing report always shows the same rows whether or not each
   * one holds a value.
   *
   * @return an immutable list in report order
   */
  public static List<AgingBucket> reportOrder() {
    return Collections.unmodifiableList(Arrays.asList(values()));
  }
}
