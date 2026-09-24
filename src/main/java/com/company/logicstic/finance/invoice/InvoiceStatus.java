package com.company.logicstic.finance.invoice;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Canonical invoice statuses, from {@code docs/docs/business-spec.md} §3.1.
 *
 * <p>The lifecycle is {@code Draft → Issued/Sent → PartiallyPaid/Paid → Cancelled}, with payroll
 * invoices carrying an approval branch. {@code PartiallyPaid} is derived rather than stored by the
 * payment application rule in the same section ({@code paid >= total ? Paid : PartiallyPaid}).
 *
 * <p><strong>Why this enum exists.</strong> {@code invoices.status} is a legacy free-text column
 * ({@code text NOT NULL}) with no check constraint and no normalisation. The repository writes two
 * spellings of the same state — {@code DataSeeder} writes {@code "Draft"}/{@code "Issued"}/ {@code
 * "Paid"} while {@link InvoiceDispatchStatus} writes {@code "draft"}/{@code "issued"} — so any
 * aggregate that compares the raw string either misses rows or silently drops them. Reporting
 * therefore always compares {@code LOWER(i.status) IN :statuses} against the constants here, and
 * the set it filters on is a business decision recorded in code and pinned by a test rather than a
 * string literal scattered through a query.
 *
 * <p>Multi-word statuses accept both the run-together and the underscore spelling because the spec
 * is ambiguous about which one the legacy .NET service wrote, and neither spelling has been
 * observed in this repository's own code. Accepting both is a statement about spelling, not about
 * the state machine: they name the same state.
 */
public enum InvoiceStatus {
  /** Created but not yet billed to the customer. Not revenue. */
  DRAFT("draft"),
  /** Billed to the customer. */
  ISSUED("issued"),
  /**
   * Billed to the customer. The spec writes this stage as "Issued/Sent" — one stage, two words — so
   * both spellings are revenue-bearing.
   */
  SENT("sent"),
  /** Partly collected. */
  PARTIALLY_PAID("partiallypaid", "partially_paid"),
  /** Fully collected. */
  PAID("paid"),
  /** Withdrawn before collection. Never revenue. */
  CANCELLED("cancelled", "canceled"),
  /** Payroll branch: awaiting approval before it becomes payable. */
  PENDING_APPROVAL("pendingapproval", "pending_approval"),
  /** Payroll branch: approved. */
  APPROVED("approved"),
  /** Payroll branch: rejected. Never revenue. */
  REJECTED("rejected");

  /**
   * Statuses that represent money actually billed to a customer.
   *
   * <p>{@link #DRAFT} is excluded because an unbilled invoice is not revenue, and {@link
   * #CANCELLED} because a cancelled invoice will never be collected. Including either would inflate
   * the headline revenue figure on the executive screen.
   */
  private static final Set<InvoiceStatus> REVENUE_BEARING =
      Collections.unmodifiableSet(EnumSet.of(ISSUED, SENT, PARTIALLY_PAID, PAID));

  private final String canonicalValue;
  private final Set<String> acceptedValues;

  InvoiceStatus(String canonicalValue, String... alternativeValues) {
    this.canonicalValue = canonicalValue;
    Set<String> values = new LinkedHashSet<>();
    values.add(canonicalValue);
    Collections.addAll(values, alternativeValues);
    this.acceptedValues = Collections.unmodifiableSet(values);
  }

  /** The lowercase spelling written to the database by this repository's own code. */
  public String dbValue() {
    return canonicalValue;
  }

  /**
   * Every lowercase spelling this status is known by, for a {@code LOWER(status) IN (...)}
   * predicate.
   *
   * <p>Lowercase is not cosmetic: the column holds both {@code "Issued"} and {@code "issued"}, so
   * the comparison must be case-folded on both sides or the aggregate silently undercounts.
   */
  public Set<String> acceptedLowercaseValues() {
    return acceptedValues;
  }

  /** Case-insensitive match against a stored value, trimming incidental whitespace. */
  public boolean matches(String value) {
    return value != null && acceptedValues.contains(value.trim().toLowerCase(Locale.ROOT));
  }

  /** Parses a stored value, or {@code null} when the column holds something unrecognised. */
  public static InvoiceStatus fromDbValue(String value) {
    for (InvoiceStatus status : values()) {
      if (status.matches(value)) {
        return status;
      }
    }
    return null;
  }

  /**
   * Lowercase spellings of every revenue-bearing status, ready to bind into {@code LOWER(i.status)
   * IN :statuses}.
   *
   * @return an immutable set that is never empty
   */
  public static Set<String> revenueBearingLowercase() {
    Set<String> values = new LinkedHashSet<>();
    for (InvoiceStatus status : REVENUE_BEARING) {
      values.addAll(status.acceptedValues);
    }
    return Collections.unmodifiableSet(values);
  }
}
