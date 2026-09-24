package com.company.logicstic.reporting;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the aggregate actually read, so a caller can tell an empty result from a zero one.
 *
 * <p>Every aggregate response carries this block. The metrics alone cannot distinguish "this tenant
 * has no expenses recorded" from "expenses exist but totalled zero", and those two situations call
 * for opposite reactions from whoever is looking at the dashboard — the first is a data-entry
 * problem, the second is a business fact. Rather than asking the reader to trust the service, the
 * response states how many rows it read, in which currencies, what values the free-text columns
 * held, and how many rows it had to skip.
 *
 * @param currency the currency every monetary figure in this response is denominated in
 * @param sourceRowCounts rows read per source table, by the name used in this API
 * @param excludedOtherCurrencyRows rows that matched every other filter but were denominated in a
 *     different currency. Because the currency predicate runs inside the query, this is a real
 *     count of rows actually skipped, not an estimate.
 * @param currenciesPresent every distinct currency among the rows that matched the non-currency
 *     filters. A populated list that excludes {@link #currency} explains an otherwise puzzling
 *     empty result.
 * @param distributions raw value counts for the free-text columns this report grouped on, keyed by
 *     column meaning — {@code invoiceStatus}, {@code truckStatus} and so on. These are reported
 *     unnormalised, and that is the point: none of these columns has an enforced vocabulary, so a
 *     value this deployment has never seen appears here as its own row instead of being folded into
 *     an "other" bucket that would hide it. When a figure looks wrong, this is where to check
 *     whether the rows behind it were the ones intended.
 * @param unavailableMetrics identifiers of the metrics in this response that carry no number
 */
public record DataCompleteness(
    String currency,
    Map<String, Long> sourceRowCounts,
    long excludedOtherCurrencyRows,
    List<String> currenciesPresent,
    Map<String, List<GroupCount>> distributions,
    List<String> unavailableMetrics) {

  public DataCompleteness {
    sourceRowCounts =
        sourceRowCounts == null
            ? Map.of()
            : Collections.unmodifiableMap(new TreeMap<>(sourceRowCounts));
    currenciesPresent =
        currenciesPresent == null ? List.of() : List.copyOf(new TreeSet<>(currenciesPresent));
    distributions =
        distributions == null
            ? Map.of()
            : Collections.unmodifiableMap(new TreeMap<>(distributions));
    unavailableMetrics = unavailableMetrics == null ? List.of() : List.copyOf(unavailableMetrics);
  }

  /** Convenience builder for a response that skipped no rows for currency reasons. */
  public static DataCompleteness of(
      String currency, Map<String, Long> sourceRowCounts, List<String> unavailableMetrics) {
    return new DataCompleteness(
        currency, sourceRowCounts, 0L, List.of(currency), Map.of(), unavailableMetrics);
  }

  /**
   * The same block with the free-text distributions filled in.
   *
   * <p>A separate method rather than a longer constructor call because most responses have exactly
   * one or two distributions, and passing an empty map to every construction site buries the ones
   * that matter.
   */
  public DataCompleteness withDistributions(Map<String, List<GroupCount>> newDistributions) {
    return new DataCompleteness(
        currency,
        sourceRowCounts,
        excludedOtherCurrencyRows,
        currenciesPresent,
        newDistributions,
        unavailableMetrics);
  }
}
