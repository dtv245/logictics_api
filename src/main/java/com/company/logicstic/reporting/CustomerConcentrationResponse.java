package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Revenue concentration across customers.
 *
 * <p><strong>The shares and the index describe every customer; {@code rows} describes only the
 * largest ones.</strong> That asymmetry is deliberate and load-bearing. A share computed from a
 * truncated list is divided by a truncated total, so it states the customer's share of whatever
 * happened to be returned rather than of revenue — and because truncation drops the smallest
 * customers, the denominator falls faster than the numerators, so every retained share and the
 * index with them read <em>higher</em> than the truth. A client that recomputed either figure from
 * {@code rows} would therefore overstate concentration risk. Either direction is a wrong number on
 * the one reading this endpoint exists to produce, which is why the index is computed server-side
 * and handed over whole. {@code customersConsidered} is returned precisely so a caller can prove
 * the difference: when it exceeds {@code customersReturned}, the list is truncated and the figures
 * beside it do not come from it.
 *
 * @param currency revenue is denominated in this currency
 * @param from inclusive start of the revenue window
 * @param to exclusive end of the revenue window
 * @param top1Share the largest customer's share of revenue, as a percentage
 * @param top3Share the three largest customers' combined share, as a percentage
 * @param top5Share the five largest customers' combined share. Computed over the whole customer
 *     set, never by adding up whatever rows happen to be present — with fewer than five customers
 *     returned that sum would come out lower than the truth
 * @param hhi Herfindahl-Hirschman index over <em>all</em> customers, on a 0–10,000 scale. Always
 *     computed server-side for the reason above
 * @param customersConsidered customers with revenue in the window, before truncation
 * @param customersReturned how many appear in {@code rows}
 * @param invoicesWithoutCustomer revenue-bearing invoices in the window that name no customer. This
 *     is the part of revenue that is in the total but can never appear in {@code rows}, so it is
 *     the gap between "the shares sum to 100%" and the truth: with rows excluded here, the
 *     displayed shares sum to less than the whole, and this count is why
 * @param rows the largest customers by revenue, descending, truncated to the requested limit
 * @param completeness what the aggregate read
 */
public record CustomerConcentrationResponse(
    String currency,
    OffsetDateTime from,
    OffsetDateTime to,
    MetricValue top1Share,
    MetricValue top3Share,
    MetricValue top5Share,
    MetricValue hhi,
    int customersConsidered,
    int customersReturned,
    long invoicesWithoutCustomer,
    List<Row> rows,
    DataCompleteness completeness) {

  /**
   * One customer's revenue and service record.
   *
   * @param customerId customer identifier
   * @param customerName customer display name
   * @param revenue revenue in the window
   * @param shareOfRevenue this customer's share of total revenue, as a percentage
   * @param revenuePerMile revenue over the miles on this customer's delivered loads. Unavailable
   *     when those loads recorded no distance
   * @param grossMarginPercent always unavailable. Margin needs cost attributed to a customer, and
   *     nothing in this schema links an expense to one: expenses carry a vehicle, not a shipment.
   *     This is a permanent structural limit, not a missing feed
   * @param onTimeDeliveryPercent delivered loads that met their requested delivery date
   * @param dsoDays this customer's days sales outstanding, weighted by outstanding amount.
   *     Unavailable when the customer has nothing outstanding
   */
  public record Row(
      UUID customerId,
      String customerName,
      MetricValue revenue,
      MetricValue shareOfRevenue,
      MetricValue revenuePerMile,
      MetricValue grossMarginPercent,
      MetricValue onTimeDeliveryPercent,
      MetricValue dsoDays) {}
}
