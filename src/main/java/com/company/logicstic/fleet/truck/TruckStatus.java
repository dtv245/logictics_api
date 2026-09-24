package com.company.logicstic.fleet.truck;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lifecycle state of a {@link Truck}.
 *
 * <p><strong>Evidence, not invention.</strong> {@code trucks.status} is a legacy free-text column
 * with no check constraint, and no shipped document enumerates its values. Only two spellings are
 * observable in this repository: {@code DataSeeder} writes {@code "Active"}, while {@code
 * TruckMapperTest} uses {@code "ACTIVE"} and {@code "INACTIVE"}. Those are the members below. The
 * enum is deliberately not a guess at a richer taxonomy — a truck marked "In Shop" or "Available"
 * would not be silently classified here, it would fall through {@link #fromDbValue} as {@code null}
 * and show up in the reporting layer's raw status distribution, where a human can see it.
 *
 * <p>Matching is case-folded for the same reason as everywhere else in this codebase: the same
 * state is already stored under more than one casing.
 */
public enum TruckStatus {
  /** In service. The only value this repository's seeder writes. */
  ACTIVE("active"),
  /** Out of service. Observed in {@code TruckMapperTest}. */
  INACTIVE("inactive");

  private final String canonicalValue;

  TruckStatus(String canonicalValue) {
    this.canonicalValue = canonicalValue;
  }

  /** The lowercase spelling this enum treats as canonical. */
  public String dbValue() {
    return canonicalValue;
  }

  /** Case-insensitive match against a stored value, trimming incidental whitespace. */
  public boolean matches(String value) {
    return value != null && canonicalValue.equals(value.trim().toLowerCase(Locale.ROOT));
  }

  /** Parses a stored value, or {@code null} when the column holds something unrecognised. */
  public static TruckStatus fromDbValue(String value) {
    for (TruckStatus status : values()) {
      if (status.matches(value)) {
        return status;
      }
    }
    return null;
  }

  /**
   * Lowercase spellings that mean "this truck is in service", ready to bind into {@code
   * LOWER(t.status) IN :statuses}.
   *
   * @return an immutable set containing the one word this repository writes for an in-service truck
   */
  public static Set<String> activeLowercase() {
    Set<String> values = new LinkedHashSet<>();
    values.add(ACTIVE.canonicalValue);
    return Collections.unmodifiableSet(values);
  }
}
