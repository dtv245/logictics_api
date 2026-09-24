package com.company.logicstic.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the customer status vocabulary the reporting layer filters on.
 *
 * <p>Only {@code "Active"} is observable in this repository — {@code DataSeeder} writes it for
 * every customer it creates — and {@link CustomerStatus#INACTIVE} carries no observed rows. Naming
 * it anyway is the distinction that matters for the concentration report: "not trading" and
 * "deleted" are different facts, and a report that could not state the first would have to treat
 * every non-active customer as an unknown word.
 */
class CustomerStatusTest {

  @Test
  @DisplayName("counts only the observed trading word as active")
  void should_treat_only_the_observed_spelling_as_active() {
    assertThat(CustomerStatus.activeLowercase()).containsExactly("active");
  }

  @Test
  @DisplayName("resolves the seeder's spelling and its uppercase form")
  void should_resolve_the_observed_casing() {
    assertThat(CustomerStatus.fromDbValue("Active")).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(CustomerStatus.fromDbValue("ACTIVE")).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(CustomerStatus.fromDbValue("Inactive")).isEqualTo(CustomerStatus.INACTIVE);
  }

  @Test
  @DisplayName("returns null rather than guessing at an unrecognised status")
  void should_return_null_for_an_unrecognised_status() {
    assertThat(CustomerStatus.fromDbValue("Prospect")).isNull();
    assertThat(CustomerStatus.fromDbValue("On Hold")).isNull();
    assertThat(CustomerStatus.fromDbValue(null)).isNull();
  }

  @Test
  @DisplayName("emits the active spelling already lowercased")
  void should_never_leak_a_capitalised_literal_into_the_query() {
    assertThat(CustomerStatus.activeLowercase())
        .allSatisfy(value -> assertThat(value).isEqualTo(value.toLowerCase(Locale.ROOT)));
  }

  @Test
  @DisplayName("hands out an immutable set that the caller cannot widen")
  void should_not_let_a_caller_mutate_the_active_set() {
    assertThatThrownBy(() -> CustomerStatus.activeLowercase().add("prospect"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
