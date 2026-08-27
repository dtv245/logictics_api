package com.company.logicstic.modules.finance.enums;

/** Status values owned by the Load-dispatch invoice transition. */
public enum InvoiceDispatchStatus {
  DRAFT("draft"),
  ISSUED("issued");

  private final String dbValue;

  InvoiceDispatchStatus(String dbValue) {
    this.dbValue = dbValue;
  }

  public String dbValue() {
    return dbValue;
  }

  /** Matches canonical and legacy casing without changing unrelated invoice status values. */
  public boolean matches(String value) {
    return value != null && dbValue.equalsIgnoreCase(value.trim());
  }

  public static InvoiceDispatchStatus fromDbValue(String value) {
    for (InvoiceDispatchStatus status : values()) {
      if (status.matches(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unsupported dispatch invoice status: " + value);
  }

  public static boolean isValidTransition(
      InvoiceDispatchStatus current, InvoiceDispatchStatus next) {
    return current == DRAFT && next == ISSUED;
  }
}
