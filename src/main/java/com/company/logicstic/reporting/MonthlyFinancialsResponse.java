package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Revenue and cost by calendar month.
 *
 * @param currency every monetary figure is denominated in this currency, and nothing else. A {@code
 *     SUM} across currencies would be meaningless, so rows in other currencies are excluded inside
 *     the query and reported as a count in {@code completeness}
 * @param from inclusive start of the window
 * @param to exclusive end of the window
 * @param points one entry per month that had any activity, in chronological order
 * @param completeness what the aggregate read
 */
public record MonthlyFinancialsResponse(
    String currency,
    OffsetDateTime from,
    OffsetDateTime to,
    List<Point> points,
    DataCompleteness completeness) {

  /**
   * One calendar month.
   *
   * @param month {@code YYYY-MM}, for use as a chart key
   * @param label the same month rendered for display
   * @param revenue invoiced revenue in the month. Credit notes are included as stored rather than
   *     netted off: the schema has no sign convention for them, so subtracting would invent one
   * @param operatingCost cost allocated to the month. Unavailable whenever the expense table has no
   *     rows in this currency — which is the normal state today, since nothing writes expenses
   * @param operatingProfit unavailable whenever cost is. Never silently equal to revenue
   * @param revenuePerMile revenue over loaded miles. Unavailable when the month has no delivered
   *     miles: a rate over zero miles is unknown, not zero
   * @param costPerMile unavailable whenever cost is
   * @param contributionSpread revenue per mile minus cost per mile. Unavailable when either side is
   *     — it must not collapse into a restatement of revenue per mile
   * @param loadedMiles miles on loads delivered in the month
   * @param totalMiles never available. {@code totalMiles} would include empty and deadhead miles,
   *     which nothing records; the field exists so the contract's key is present and honest, rather
   *     than being filled with {@code loads.distance} under a name that means something wider
   * @param closed whether the month has ended. This is a statement about the calendar, not about
   *     accounting: no {@code accounting_periods} table exists, so nothing here knows whether a
   *     period has been formally closed for reporting
   * @param invoiceCount invoices behind {@code revenue}
   */
  public record Point(
      String month,
      String label,
      MetricValue revenue,
      MetricValue operatingCost,
      MetricValue operatingProfit,
      MetricValue revenuePerMile,
      MetricValue costPerMile,
      MetricValue contributionSpread,
      MetricValue loadedMiles,
      MetricValue totalMiles,
      boolean closed,
      long invoiceCount) {}
}
