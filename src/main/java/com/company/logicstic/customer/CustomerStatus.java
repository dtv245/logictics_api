package com.company.logicstic.customer;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lifecycle state of a {@link Customer}.
 *
 * <p><strong>Evidence, not invention.</strong> {@code customers.status} is a legacy free-text
 * column with no check constraint. The only value observable in this repository is {@code
 * "Active"}, which {@code DataSeeder} writes for every customer it creates. {@link #INACTIVE} is
 * the negated state of the same lifecycle and carries no observed rows; it is named here so that
 * the reporting layer can state the distinction rather than treat "not active" as "deleted". A
 * value this enum does not recognise is never dropped — it falls through {@link #fromDbValue} as
 * {@code null} and appears in the reporting layer's raw status distribution.
 */
public enum CustomerStatus {
  /** Trading. The only value this repository's seeder writes. */
  ACTIVE("active"),
  /** Not trading. No rows observed; named for completeness of the lifecycle. */
  INACTIVE("inactive");

  private final String canonicalValue;

  CustomerStatus(String canonicalValue) {
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
  public static CustomerStatus fromDbValue(String value) {
    for (CustomerStatus status : values()) {
      if (status.matches(value)) {
        return status;
      }
    }
    return null;
  }

  /**
   * Lowercase spellings that mean "this customer is trading", ready to bind into {@code
   * LOWER(c.status) IN :statuses}.
   *
   * @return an immutable set containing the one word this repository writes for an active customer
   */
  public static Set<String> activeLowercase() {
    Set<String> values = new LinkedHashSet<>();
    values.add(ACTIVE.canonicalValue);
    return Collections.unmodifiableSet(values);
  }
}
