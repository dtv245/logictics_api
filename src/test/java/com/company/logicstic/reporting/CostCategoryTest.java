package com.company.logicstic.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins how an expense row with no enforced vocabulary is placed in the cost-structure chart.
 *
 * <p>{@code expenses.type}, {@code expenses.category} and {@code expenses.truck_expense_category}
 * are all legacy free-text columns, and this repository contains no observed value for any of them
 * — there is no expense seeder and no test constructs an {@code Expense}. Classification is
 * therefore a keyword guess, and the behaviour under test is what happens when the guess fails.
 *
 * <p>The rule this class protects: a row whose wording is not recognised is
 * <strong>counted</strong> under {@link CostCategory#UNCLASSIFIED}, never dropped and never
 * attributed to a bucket it does not belong to. Dropping it would make the cost total smaller than
 * the money actually spent, which is the one error a cost report must not make.
 */
class CostCategoryTest {

  /**
   * One representative legacy wording per bucket, deliberately chosen so it contains no keyword of
   * an earlier bucket — otherwise a passing test would not show that the intended bucket matched.
   */
  private static Map<String, CostCategory> representativeWords() {
    Map<String, CostCategory> words = new LinkedHashMap<>();
    words.put("Diesel Fuel", CostCategory.FUEL);
    words.put("Driver Payroll", CostCategory.DRIVER_PAY);
    words.put("Brake Repair", CostCategory.MAINTENANCE);
    words.put("Tyre Replacement", CostCategory.TIRES);
    words.put("Weigh Station Fee", CostCategory.TOLLS);
    words.put("Cargo Insurance Premium", CostCategory.INSURANCE);
    words.put("Office Supplies", CostCategory.OTHER);
    return words;
  }

  @Test
  @DisplayName("classifies a representative wording into every bucket")
  void should_classify_a_representative_word_into_each_bucket() {
    representativeWords()
        .forEach(
            (word, expected) ->
                assertThat(CostCategory.classify(word, null, null))
                    .as("'%s'", word)
                    .isEqualTo(expected));
  }

  @Test
  @DisplayName("keeps an unrecognised row as unclassified rather than dropping it")
  void should_fall_back_to_unclassified_rather_than_dropping_a_row() {
    // The three legacy discriminators the business spec names — CompanyExpense, TruckExpense,
    // BodyShopExpense — say which table a row came from, not what was bought, so none of them can
    // place a cost. They land in UNCLASSIFIED, which is a statement that the money is real and its
    // category is unknown. A silent drop would understate total cost instead.
    assertThat(CostCategory.classify("TruckExpense", null, null))
        .isEqualTo(CostCategory.UNCLASSIFIED);
    assertThat(CostCategory.classify("Miscellaneous Vendor Charge", null, null))
        .isEqualTo(CostCategory.UNCLASSIFIED);
  }

  @Test
  @DisplayName("never returns unclassified for a row it can place")
  void should_not_send_a_recognisable_row_to_unclassified() {
    // Stated from the other side: UNCLASSIFIED must not become a bucket of convenience. Its size is
    // a measure of how much of the cost base this enum cannot read, so it is only meaningful while
    // it stays empty for rows that do carry a recognisable word.
    assertThat(CostCategory.classify("Diesel", null, null)).isNotEqualTo(CostCategory.UNCLASSIFIED);
    assertThat(CostCategory.classify(null, "PREVENTIVE_MAINTENANCE", null))
        .isEqualTo(CostCategory.MAINTENANCE);
    assertThat(CostCategory.classify(null, null, "truck-repair"))
        .isEqualTo(CostCategory.MAINTENANCE);
  }

  @Test
  @DisplayName("treats a row with no wording at all as unclassified")
  void should_treat_a_row_with_no_wording_as_unclassified() {
    assertThat(CostCategory.classify(null, null, null)).isEqualTo(CostCategory.UNCLASSIFIED);
    assertThat(CostCategory.classify("", "   ", null)).isEqualTo(CostCategory.UNCLASSIFIED);
  }

  @Test
  @DisplayName("classifies from whichever of the three columns carries the word")
  void should_classify_from_any_of_the_three_columns() {
    // Which column a tenant used is a property of their legacy system, not of the cost. Reading all
    // three and taking any hit is what makes the chart work across tenants.
    assertThat(CostCategory.classify("Toll", null, null)).isEqualTo(CostCategory.TOLLS);
    assertThat(CostCategory.classify(null, "Bridge toll", null)).isEqualTo(CostCategory.TOLLS);
    assertThat(CostCategory.classify(null, null, "toll road")).isEqualTo(CostCategory.TOLLS);
  }

  @Test
  @DisplayName("matches a keyword inside the wording, not only as a whole word")
  void should_match_a_keyword_embedded_in_the_wording() {
    // Substring matching is the deliberate behaviour: the vocabulary is unenumerated free text, so
    // a
    // word-boundary match would miss "Preventative-Maintenance" and every inflected form beside it.
    // The cost is that a longer word containing a keyword also matches.
    assertThat(CostCategory.classify("Preventative-Maintenance Service", null, null))
        .isEqualTo(CostCategory.MAINTENANCE);
    assertThat(CostCategory.classify("DRIVER_PAY", null, null)).isEqualTo(CostCategory.DRIVER_PAY);
  }

  @Test
  @DisplayName("resolves a row matching two buckets to the first one declared")
  void should_resolve_a_two_bucket_row_to_the_first_declared() {
    // Declaration order is the tie-break, and it is arbitrary but fixed. Asserting it makes the
    // ordering a decision the next editor has to change on purpose rather than by moving a line.
    // FUEL is declared before MAINTENANCE, and MAINTENANCE before OTHER, so each row lands on the
    // earlier of the two buckets it matches.
    assertThat(CostCategory.classify("Fuel and Maintenance", null, null))
        .isEqualTo(CostCategory.FUEL);
    assertThat(CostCategory.classify("Maintenance Shop Supplies", null, null))
        .isEqualTo(CostCategory.MAINTENANCE);
  }

  @Test
  @DisplayName("emits every bucket so the chart series is stable between periods")
  void should_emit_every_bucket_in_chart_order() {
    // An empty bucket still appears, so a category that falls to zero is visibly zero rather than
    // absent, and shareOfTotal has the same denominator from one period to the next.
    assertThat(CostCategory.chartOrder()).containsExactly(CostCategory.values());
    assertThat(CostCategory.chartOrder()).endsWith(CostCategory.UNCLASSIFIED);
  }

  @Test
  @DisplayName("gives every bucket a stable id and a distinct frontend key")
  void should_give_every_bucket_a_stable_id_and_label_key() {
    assertThat(CostCategory.chartOrder())
        .allSatisfy(
            category -> {
              assertThat(category.id()).isNotBlank();
              assertThat(category.labelKey()).startsWith("executive.costCategories.");
            });
    // Distinct ids and keys, because both are used as map keys downstream: a duplicate would make
    // two categories share a slice of the chart.
    assertThat(CostCategory.chartOrder()).extracting(CostCategory::id).doesNotHaveDuplicates();
    assertThat(CostCategory.chartOrder())
        .extracting(CostCategory::labelKey)
        .doesNotHaveDuplicates();
  }
}
