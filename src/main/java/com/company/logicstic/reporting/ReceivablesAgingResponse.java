package com.company.logicstic.reporting;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Outstanding receivables, aged.
 *
 * <p>Outstanding is derived from money actually received — {@code total_amount} minus the sum of
 * settled payments — rather than from the invoice's status word. {@code invoices.status} is legacy
 * free text that this repository already writes in two casings, so a status-driven total would
 * inherit that ambiguity. The status-driven figure is still computed, and the two are compared:
 * {@code invoicesWithStatusPaymentMismatch} counts invoices where they disagree. That turns the
 * known status problem into a number on the report instead of a hidden source of doubt.
 *
 * @param currency outstanding amounts are denominated in this currency
 * @param asOf the instant the ageing is measured at
 * @param revenueWindowFrom inclusive start of the window DSO divides by
 * @param revenueWindowTo exclusive end of that window
 * @param outstandingTotal unpaid balance across all open invoices. Unavailable when no invoice
 *     exists at all in this currency
 * @param dsoDays days sales outstanding: outstanding balance over revenue per day. Unavailable when
 *     the window's revenue is zero — DSO over no sales is unknown, not zero. The window is echoed
 *     above because DSO is meaningless without it: 40 days measured over one month of sales and 40
 *     days measured over a year are different claims
 * @param overdueTotal unpaid balance past its due date. Rows with no due date are excluded, not
 *     treated as current
 * @param buckets one entry per {@link AgingBucket}, always all of them, in report order
 * @param invoicesWithStatusPaymentMismatch invoices whose status and payment records disagree about
 *     whether they are settled. A data-quality signal, reported rather than resolved
 * @param completeness what the aggregate read
 */
public record ReceivablesAgingResponse(
    String currency,
    OffsetDateTime asOf,
    OffsetDateTime revenueWindowFrom,
    OffsetDateTime revenueWindowTo,
    MetricValue outstandingTotal,
    MetricValue dsoDays,
    MetricValue overdueTotal,
    List<Bucket> buckets,
    long invoicesWithStatusPaymentMismatch,
    DataCompleteness completeness) {

  /**
   * One ageing band.
   *
   * @param id stable identifier from {@link AgingBucket#id()}
   * @param labelKey frontend i18n key
   * @param amount unpaid balance in this band
   * @param shareOfOutstanding this band's share of {@code outstandingTotal}. Unavailable when
   *     nothing is outstanding
   * @param invoiceCount open invoices in this band
   */
  public record Bucket(
      String id,
      String labelKey,
      MetricValue amount,
      MetricValue shareOfOutstanding,
      long invoiceCount) {}
}
