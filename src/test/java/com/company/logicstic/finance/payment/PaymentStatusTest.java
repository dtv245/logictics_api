package com.company.logicstic.finance.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins which payment statuses mean "the money arrived".
 *
 * <p>{@code payments.status} carries two words for a settled payment against real rows: {@code
 * DataSeeder} writes {@code "completed"} and {@code ApiFunctionalIT} writes {@code "paid"}. Neither
 * is a typo to be tidied away — they are the data, and an aggregate that bound only one of them
 * would under-report cash collected. On the receivables report that error runs in the worst
 * possible direction: every payment it misses becomes debt the customer appears to still owe.
 */
class PaymentStatusTest {

  @Test
  @DisplayName("counts both observed words for a settled payment")
  void should_treat_completed_and_paid_as_settled() {
    assertThat(PaymentStatus.settledLowercase()).containsExactlyInAnyOrder("completed", "paid");
  }

  @Test
  @DisplayName("excludes money that has not arrived")
  void should_exclude_pending_failed_and_refunded_payments() {
    // Pending is in flight and failed never settled, so neither is cash. Refunded was received and
    // given back: counting it would report money the company no longer holds, which is the same
    // wrong answer as counting a failed payment, arrived at from the other side.
    assertThat(PaymentStatus.settledLowercase()).doesNotContain("pending", "failed", "refunded");
  }

  @Test
  @DisplayName("emits every settled spelling already lowercased")
  void should_never_leak_a_capitalised_literal_into_the_query() {
    assertThat(PaymentStatus.settledLowercase())
        .allSatisfy(value -> assertThat(value).isEqualTo(value.toLowerCase(Locale.ROOT)));
  }

  @Test
  @DisplayName("resolves an observed status through fromDbValue")
  void should_resolve_each_settled_spelling_to_its_own_state() {
    // The two words are kept as separate members rather than folded to one constant precisely so
    // that the raw status distribution can still report which word a row actually carries.
    assertThat(PaymentStatus.fromDbValue("completed")).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(PaymentStatus.fromDbValue("PAID")).isEqualTo(PaymentStatus.PAID);
    assertThat(PaymentStatus.fromDbValue("  Failed ")).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  @DisplayName("returns null rather than guessing at a status it does not know")
  void should_return_null_for_an_unrecognised_status() {
    assertThat(PaymentStatus.fromDbValue("settled")).isNull();
    assertThat(PaymentStatus.fromDbValue(null)).isNull();
  }

  @Test
  @DisplayName("hands out an immutable set that the caller cannot widen")
  void should_not_let_a_caller_mutate_the_settled_set() {
    assertThatThrownBy(() -> PaymentStatus.settledLowercase().add("pending"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
