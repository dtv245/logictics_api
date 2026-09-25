package com.company.logicstic.fleet.truck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the fleet status vocabulary the reporting layer filters on.
 *
 * <p>The members here are the only spellings observable in this repository — {@code DataSeeder}
 * writes {@code "Active"} and {@code TruckMapperTest} uses {@code "ACTIVE"}/{@code "INACTIVE"} —
 * and that is deliberately the whole list. Truck status is the denominator of every fleet-health
 * percentage, so an invented member would silently move a figure the executive screen presents as a
 * measurement. A real status this enum has never seen ({@code "In Shop"}, {@code "Available"}) is
 * meant to fall through as {@code null} and appear in the raw distribution instead.
 */
class TruckStatusTest {

  @Test
  @DisplayName("counts only the observed in-service words as active")
  void should_treat_only_the_observed_spelling_as_active() {
    assertThat(TruckStatus.activeLowercase()).containsExactly("active");
  }

  @Test
  @DisplayName("resolves both casings this repository writes")
  void should_resolve_every_observed_casing() {
    assertThat(TruckStatus.fromDbValue("Active")).isEqualTo(TruckStatus.ACTIVE);
    assertThat(TruckStatus.fromDbValue("ACTIVE")).isEqualTo(TruckStatus.ACTIVE);
    assertThat(TruckStatus.fromDbValue("INACTIVE")).isEqualTo(TruckStatus.INACTIVE);
  }

  @Test
  @DisplayName("returns null for a status no shipped document enumerates")
  void should_not_guess_at_a_status_the_vocabulary_does_not_contain() {
    // The point of the enum is that it does not invent a taxonomy. If a tenant writes "In Shop",
    // the honest answer is "this enum does not know that word", which the reporting layer turns
    // into a visible row in the raw distribution rather than a truck quietly counted as active.
    assertThat(TruckStatus.fromDbValue("In Shop")).isNull();
    assertThat(TruckStatus.fromDbValue("Available")).isNull();
    assertThat(TruckStatus.fromDbValue(null)).isNull();
  }

  @Test
  @DisplayName("emits the active spelling already lowercased")
  void should_never_leak_a_capitalised_literal_into_the_query() {
    assertThat(TruckStatus.activeLowercase())
        .allSatisfy(value -> assertThat(value).isEqualTo(value.toLowerCase(Locale.ROOT)));
  }

  @Test
  @DisplayName("hands out an immutable set that the caller cannot widen")
  void should_not_let_a_caller_mutate_the_active_set() {
    assertThatThrownBy(() -> TruckStatus.activeLowercase().add("in shop"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
