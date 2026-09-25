package com.company.logicstic.reporting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Cost buckets for the executive cost-structure chart.
 *
 * <p><strong>This is a presentation taxonomy, not a domain enum, and it is deliberately
 * tolerant.</strong> {@code expenses.type} is a legacy free-text column with no check constraint,
 * and this repository contains <em>no observed value for it at all</em> — there is no expense
 * seeder, and no test constructs an {@code Expense}. {@code docs/docs/business-spec.md} §3.6 names
 * three legacy discriminators ({@code CompanyExpense}, {@code TruckExpense}, {@code
 * BodyShopExpense}) that describe which table a row came from, not what was bought, so they cannot
 * drive a cost-structure chart either.
 *
 * <p>Classification is therefore keyword matching over the three free-text columns that might carry
 * a meaning ({@code type}, {@code category}, {@code truckExpenseCategory}), with {@link
 * #UNCLASSIFIED} as the fallback. That fallback is the point: a row whose wording this enum does
 * not recognise lands in {@code UNCLASSIFIED} and its money is <em>still counted</em>, under a
 * label that says "we could not classify this", instead of being dropped from the chart or silently
 * attributed to a bucket it does not belong to. The raw {@code type} distribution is reported
 * alongside so a human can see what the words actually are.
 *
 * <p>{@link #labelKey()} returns a frontend i18n key rather than a display string: this is an API
 * response, and the executive screen renders in the caller's locale.
 */
public enum CostCategory {
  FUEL("fuel", "executive.costCategories.fuel", "fuel", "diesel", "gasoline", "petrol", "def"),
  DRIVER_PAY(
      "driverPay",
      "executive.costCategories.driverPay",
      "driver",
      "payroll",
      "wage",
      "salary",
      "settlement",
      "commission"),
  MAINTENANCE(
      "maintenance",
      "executive.costCategories.maintenance",
      "maintenance",
      "repair",
      "service",
      "shop",
      "parts",
      "labour",
      "labor"),
  TIRES("tires", "executive.costCategories.tires", "tire", "tyre"),
  TOLLS("tolls", "executive.costCategories.tolls", "toll", "weigh", "scale", "parking"),
  INSURANCE("insurance", "executive.costCategories.insurance", "insurance", "premium"),
  OTHER(
      "other",
      "executive.costCategories.other",
      "permit",
      "registration",
      "licens",
      "tax",
      "utilit",
      "office",
      "admin",
      "supplies",
      "rent"),
  /**
   * Rows whose wording matched no bucket. Never a synonym for "other" — {@code OTHER} is a positive
   * statement that the cost is a known overhead, {@code UNCLASSIFIED} is an admission that this
   * code does not know what the row is.
   */
  UNCLASSIFIED("unclassified", "executive.costCategories.unclassified");

  private final String id;
  private final String labelKey;
  private final List<String> keywords;

  CostCategory(String id, String labelKey, String... keywords) {
    this.id = id;
    this.labelKey = labelKey;
    this.keywords = List.of(keywords);
  }

  /** Stable machine identifier, used as the chart's series key. */
  public String id() {
    return id;
  }

  /** Frontend i18n key for the category's display name. */
  public String labelKey() {
    return labelKey;
  }

  /**
   * Classifies one expense from its free-text columns.
   *
   * @param type {@code expenses.type}, required
   * @param category {@code expenses.category}, may be {@code null}
   * @param truckExpenseCategory {@code expenses.truck_expense_category}, may be {@code null}
   * @return the first matching bucket in declaration order, or {@link #UNCLASSIFIED}
   */
  public static CostCategory classify(String type, String category, String truckExpenseCategory) {
    String haystack = normalise(join(type, category, truckExpenseCategory));
    if (haystack.isEmpty()) {
      return UNCLASSIFIED;
    }
    for (CostCategory candidate : values()) {
      if (candidate == UNCLASSIFIED) {
        continue;
      }
      for (String keyword : candidate.keywords) {
        if (haystack.contains(keyword)) {
          return candidate;
        }
      }
    }
    return UNCLASSIFIED;
  }

  /**
   * Every bucket, so a caller can emit a stable series — including the ones with no rows — and keep
   * the {@code shareOfTotal} denominator comparable between periods.
   *
   * @return an immutable list in chart order
   */
  public static List<CostCategory> chartOrder() {
    return Collections.unmodifiableList(Arrays.asList(values()));
  }

  private static String join(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part != null && !part.isBlank()) {
        builder.append(part).append(' ');
      }
    }
    return builder.toString();
  }

  private static String normalise(String value) {
    return value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim();
  }
}
