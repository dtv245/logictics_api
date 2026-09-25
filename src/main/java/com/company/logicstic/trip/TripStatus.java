package com.company.logicstic.trip;

public enum TripStatus {
  DRAFT,
  DISPATCHED,
  COMPLETED,
  CANCELLED;

  public String dbValue() {
    return name().toLowerCase();
  }

  public static TripStatus fromDbValue(String value) {
    return value == null ? null : valueOf(value.toUpperCase());
  }
}
