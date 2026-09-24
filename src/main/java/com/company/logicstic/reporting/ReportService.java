package com.company.logicstic.reporting;

import java.time.OffsetDateTime;

/**
 * The six aggregates behind {@code /api/reports}.
 *
 * <p>Every method takes the currency it reports in and never assumes one. Amounts live on the rows
 * ({@code invoices.total_currency}, {@code payments.amount_currency}) in a column with no default
 * and no constraint, so a server-side default would not be a safety net — it would be a unit chosen
 * on the caller's behalf and then baked into a total that mixes currencies silently.
 *
 * <p>The period-taking methods accept either bound as {@code null} and fall back to the last twelve
 * months ending now, which is a decision {@link ReportPeriodResolver} owns and reports back in the
 * response so a caller can always see which window produced the numbers it is reading.
 */
public interface ReportService {

  /** Headline counts for the executive screen's North Star row. */
  ExecutiveSummaryResponse executiveSummary(
      String currency, OffsetDateTime from, OffsetDateTime to);

  /** Revenue and cost by calendar month over the window. */
  MonthlyFinancialsResponse monthlyFinancials(
      String currency, OffsetDateTime from, OffsetDateTime to);

  /** Cost structure by category, per mile, against the preceding window of equal length. */
  CostBreakdownResponse costsByCategory(String currency, OffsetDateTime from, OffsetDateTime to);

  /** Preventive-maintenance compliance and the metrics this schema cannot support. */
  FleetHealthResponse fleetHealth(String currency, OffsetDateTime from, OffsetDateTime to);

  /**
   * Revenue concentration across customers.
   *
   * @param limit how many of the largest customers appear in {@code rows}. The shares and the index
   *     are computed over every customer regardless, so this truncates the list without changing
   *     the figures beside it
   */
  CustomerConcentrationResponse customerConcentration(
      String currency, OffsetDateTime from, OffsetDateTime to, int limit);

  /**
   * Outstanding receivables, aged.
   *
   * @param asOf the instant the ageing is measured at, or {@code null} for now
   * @param from inclusive start of the window days-sales-outstanding divides by, or {@code null}
   * @param to exclusive end of that window, or {@code null}
   */
  ReceivablesAgingResponse receivablesAging(
      String currency, OffsetDateTime asOf, OffsetDateTime from, OffsetDateTime to);
}
