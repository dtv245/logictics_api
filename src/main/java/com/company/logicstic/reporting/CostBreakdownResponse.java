package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cost structure by category, per mile.
 *
 * @param currency every monetary figure is denominated in this currency
 * @param from inclusive start of the current window
 * @param to exclusive end of the current window
 * @param totalCost total cost in the current window. Unavailable when no cost row exists, which is
 *     what an absent expense feed looks like
 * @param ambiguousTruckLinkCount expense rows in the window whose two vehicle foreign keys name
 *     different trucks. {@code expenses} carries both a {@code truck_id} and a {@code
 *     truck_expense_truck_id}, and where they disagree the row cannot be attributed to a vehicle —
 *     so any per-vehicle reading of this chart is built on rows whose attribution is in doubt. The
 *     money is still counted, once, in its category; this number says how much of it to trust
 * @param categories one entry per {@link CostCategory}, including the empty ones, so the series is
 *     stable between periods and the chart's rows do not move around
 * @param completeness what the aggregate read
 */
public record CostBreakdownResponse(
    String currency,
    OffsetDateTime from,
    OffsetDateTime to,
    MetricValue totalCost,
    long ambiguousTruckLinkCount,
    List<Category> categories,
    DataCompleteness completeness) {

  /**
   * One cost category.
   *
   * @param id stable identifier from {@link CostCategory#id()}
   * @param labelKey frontend i18n key
   * @param currentTotal money spent in this category in the current window. A genuine zero is
   *     reported as available-and-zero: a category the business does not use really does cost zero,
   *     and saying otherwise would be its own inaccuracy
   * @param previousTotal the same figure for the preceding window of equal length
   * @param currentPerMile this category's cost over loaded miles driven. Unavailable when no miles
   *     were driven, never zero
   * @param previousPerMile the same figure for the preceding window
   * @param shareOfTotal this category's share of total cost. Unavailable when total cost is zero —
   *     there is no meaningful share of nothing
   * @param rowCount expense rows classified into this category
   */
  public record Category(
      String id,
      String labelKey,
      MetricValue currentTotal,
      MetricValue previousTotal,
      MetricValue currentPerMile,
      MetricValue previousPerMile,
      MetricValue shareOfTotal,
      long rowCount) {}
}
