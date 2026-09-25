package com.company.logicstic.finance.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins which invoice statuses count as revenue, and the casing rule every aggregate depends on.
 *
 * <p>This is not a test of a data holder. {@code revenueBearingLowercase()} decides the headline
 * revenue figure on the executive screen, and the decision is a judgement — a draft invoice is not
 * revenue, a cancelled one never will be — so it is recorded as an assertion rather than left to a
 * string literal inside a query where the next edit would change it unnoticed.
 *
 * <p>The casing half is the other reason this class exists. The column holds both {@code "Issued"}
 * (the seeder) and {@code "issued"} ({@link InvoiceDispatchStatus}), so a set that ever gained a
 * capitalised member would stop matching real rows and would drop revenue silently — a wrong number
 * rather than a failure.
 */
class InvoiceStatusTest {

  @Test
  @DisplayName("counts issued, sent and paid invoices as revenue")
  void should_treat_only_billed_statuses_as_revenue() {
    assertThat(InvoiceStatus.revenueBearingLowercase())
        .containsExactlyInAnyOrder("issued", "sent", "partiallypaid", "partially_paid", "paid");
  }

  @Test
  @DisplayName("leaves draft, cancelled and rejected invoices out of revenue")
  void should_exclude_unbilled_and_withdrawn_statuses() {
    // A draft has not been billed and a cancelled invoice never will be. Either one inside the
    // revenue set inflates the largest number on the screen, in the direction nobody questions.
    assertThat(InvoiceStatus.revenueBearingLowercase())
        .doesNotContain("draft", "cancelled", "canceled", "rejected", "pendingapproval");
  }

  @Test
  @DisplayName("emits every revenue spelling already lowercased")
  void should_never_leak_a_capitalised_literal_into_the_query() {
    // The set is bound into LOWER(i.status) IN :statuses, so a capitalised member can never match.
    // The failure is silent under-counting, which is why it is worth an assertion.
    assertThat(InvoiceStatus.revenueBearingLowercase())
        .allSatisfy(value -> assertThat(value).isEqualTo(value.toLowerCase(Locale.ROOT)));
  }

  @Test
  @DisplayName("matches a stored value however it was cased or padded")
  void should_match_a_stored_value_case_insensitively() {
    assertThat(InvoiceStatus.ISSUED.matches("Issued")).isTrue();
    assertThat(InvoiceStatus.ISSUED.matches("ISSUED")).isTrue();
    assertThat(InvoiceStatus.ISSUED.matches("  issued  ")).isTrue();
    assertThat(InvoiceStatus.DRAFT.matches("Issued")).isFalse();
    assertThat(InvoiceStatus.ISSUED.matches(null)).isFalse();
  }

  @Test
  @DisplayName("resolves both spellings of a partly paid invoice to one state")
  void should_resolve_both_spellings_of_partially_paid_to_one_state() {
    // The spec does not say which spelling the legacy .NET service wrote, and neither appears in
    // this repository's own code. Accepting both is a statement about spelling, not about the state
    // machine: they name the same state, so an aggregate that saw only one would under-count.
    assertThat(InvoiceStatus.fromDbValue("partially_paid")).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThat(InvoiceStatus.fromDbValue("PartiallyPaid")).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
  }

  @Test
  @DisplayName("accepts the american spelling of a cancelled invoice")
  void should_accept_the_american_spelling_of_cancelled() {
    assertThat(InvoiceStatus.fromDbValue("Cancelled")).isEqualTo(InvoiceStatus.CANCELLED);
    assertThat(InvoiceStatus.fromDbValue("canceled")).isEqualTo(InvoiceStatus.CANCELLED);
  }

  @Test
  @DisplayName("returns null rather than guessing at a status it does not know")
  void should_return_null_for_an_unrecognised_status() {
    // null is the signal the reporting layer uses to surface an unexpected word in its raw status
    // distribution. Guessing a nearest match here would hide the word instead.
    assertThat(InvoiceStatus.fromDbValue("AwaitingCustomerApproval")).isNull();
    assertThat(InvoiceStatus.fromDbValue("")).isNull();
    assertThat(InvoiceStatus.fromDbValue(null)).isNull();
  }

  @Test
  @DisplayName("every emitted spelling resolves back to a revenue-bearing status")
  void should_round_trip_every_revenue_spelling() {
    Set<String> revenue = InvoiceStatus.revenueBearingLowercase();

    assertThat(revenue).isNotEmpty();
    assertThat(revenue)
        .allSatisfy(
            spelling -> {
              InvoiceStatus resolved = InvoiceStatus.fromDbValue(spelling);
              assertThat(resolved).as("spelling '%s' must resolve", spelling).isNotNull();
              // Resolving is not enough: a spelling that parsed back to Draft would put an unbilled
              // invoice inside a revenue total, so the round trip must land on the revenue set.
              assertThat(revenue).contains(resolved.dbValue());
            });
  }

  @Test
  @DisplayName("hands out an immutable set that the caller cannot widen")
  void should_not_let_a_caller_mutate_the_revenue_set() {
    // The set is passed straight into a JPQL parameter. A mutable one would let any caller add
    // "draft" to the revenue filter, from anywhere, with no test able to see it happen.
    assertThatThrownBy(() -> InvoiceStatus.revenueBearingLowercase().add("draft"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
