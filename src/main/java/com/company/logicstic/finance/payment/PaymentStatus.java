package com.company.logicstic.finance.payment;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Settlement states of a {@link Payment}.
 *
 * <p><strong>Why this enum exists.</strong> {@code payments.status} is a legacy free-text column,
 * and this repository already writes two words for the same settled state: {@code DataSeeder}
 * writes {@code "completed"} while {@code ApiFunctionalIT} creates payments with {@code "paid"}.
 * Both are asserted against real rows in the test suite, so neither is a typo to be corrected —
 * they are the data. An aggregate that binds only one of them silently under-reports cash
 * collected, which on the receivables endpoint would overstate what customers still owe.
 *
 * <p>{@link #settledLowercase()} is the set that counts as "money received". {@link #PENDING} and
 * {@link #FAILED} are cash that has not arrived, and are excluded from collected totals.
 */
public enum PaymentStatus {
  /** Money received. The spelling this repository's seeder writes. */
  COMPLETED("completed"),
  /** Money received. The spelling this repository's integration test writes. */
  PAID("paid"),
  /** In flight; the money has not arrived. */
  PENDING("pending"),
  /** Did not settle. */
  FAILED("failed"),
  /** Reversed after settling. */
  REFUNDED("refunded");

  private final String canonicalValue;

  PaymentStatus(String canonicalValue) {
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
  public static PaymentStatus fromDbValue(String value) {
    for (PaymentStatus status : values()) {
      if (status.matches(value)) {
        return status;
      }
    }
    return null;
  }

  /**
   * Lowercase spellings that mean "this payment settled", ready to bind into {@code LOWER(p.status)
   * IN :statuses}.
   *
   * @return an immutable set containing both words this repository uses for a settled payment
   */
  public static Set<String> settledLowercase() {
    Set<String> values = new LinkedHashSet<>();
    values.add(COMPLETED.canonicalValue);
    values.add(PAID.canonicalValue);
    return Collections.unmodifiableSet(values);
  }
}
