package com.company.logicstic.reporting;

/**
 * Why a metric carries no number.
 *
 * <p><strong>Why this exists.</strong> The executive screen renders every metric as an object,
 * never as a bare number, because the frontend's metric adapter reads a missing value as {@code 0}
 * ({@code executive.queries.ts}: {@code value: current ?? 0}). A JSON {@code null} under a key the
 * client believes is populated would therefore be displayed as a confident zero on a screen whose
 * entire purpose is to tell leadership whether the company is healthy. "We could not compute this"
 * and "this is zero" must be distinguishable on the wire, and this enum is the vocabulary that
 * makes them so.
 *
 * <p>Each constant names a structural reason, not a bug. They are deliberately specific: "no cost
 * rows exist yet" and "cost rows exist but none was allocated to a vehicle" are different
 * situations that call for different responses from whoever reads the dashboard.
 */
public enum MetricUnavailableReason {
  /** The source table has no rows in scope for this tenant and period. */
  NO_SOURCE_ROWS,
  /** Rows exist, but none survived the currency filter. See {@code currenciesPresent}. */
  NO_ROWS_IN_CURRENCY,
  /** The metric needs a denominator that is zero. A rate over nothing is unknown, not zero. */
  ZERO_DENOMINATOR,
  /**
   * The metric needs a total-distance source. Total distance comes from odometer readings, so this
   * means no vehicle has two readings in the period to difference — not that the concept is
   * unrepresentable. Loaded distance alone is not a substitute: dividing by it would silently
   * assume every mile driven was a loaded mile.
   */
  NO_TOTAL_MILES_SOURCE,
  /**
   * The metric needs odometer readings over time and none is recorded for the period. The table for
   * these exists; nothing has written to it. Available again the moment a feed does.
   */
  NO_ODOMETER_SOURCE,
  /**
   * The metric needs to know when each vehicle was available to work. This schema records a
   * vehicle's current status, not its status history, so "miles driven over miles available" has no
   * denominator that could be reconstructed for a past window. Permanent until a fleet status
   * history exists.
   */
  NO_AVAILABILITY_HISTORY,
  /** The metric needs a downtime interval. Service dates are instants, not spans. */
  NO_DOWNTIME_INTERVALS,
  /** The metric needs downtime rows classified as planned or unplanned, and none is. */
  NO_DOWNTIME_CLASSIFICATION,
  /** No basis exists for allocating this cost to the entity the metric is reported per. */
  NO_COST_ALLOCATION,
  /** The metric needs a maintenance schedule to compare against, and none is configured. */
  NO_PM_SCHEDULE,
  /** The expense row's two vehicle foreign keys disagree, so its cost cannot be attributed. */
  AMBIGUOUS_TRUCK_LINK,
  /** The metric depends on a source that has not been implemented in this deployment. */
  NOT_IMPLEMENTED
}
