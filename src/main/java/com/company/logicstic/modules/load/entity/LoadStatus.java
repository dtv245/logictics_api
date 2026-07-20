package com.company.logicstic.modules.load.entity;

/**
 * Valid states for a Load's lifecycle.
 *
 * <p>State machine:
 *
 * <pre>
 * Draft → Dispatched → PickedUp → Delivered
 *   ↓          ↓           ↓
 * Cancelled  Cancelled  Cancelled
 * </pre>
 *
 * Delivered and Cancelled are terminal states.
 */
public enum LoadStatus {
  DRAFT,
  DISPATCHED,
  PICKED_UP,
  DELIVERED,
  CANCELLED;

  /** Returns the database representation (lowercase, used in the "status" column). */
  public String dbValue() {
    return name().toLowerCase();
  }

  /** Parses a database value (lowercase string) back to an enum. */
  public static LoadStatus fromDbValue(String value) {
    if (value == null) return null;
    return valueOf(value.toUpperCase());
  }

  /** Checks whether a transition from {@code current} to {@code next} is valid. */
  public static boolean isValidTransition(LoadStatus current, LoadStatus next) {
    if (current == null) return next == DRAFT;
    return switch (current) {
      case DRAFT -> next == DISPATCHED || next == CANCELLED;
      case DISPATCHED -> next == PICKED_UP || next == CANCELLED;
      case PICKED_UP -> next == DELIVERED || next == CANCELLED;
      case DELIVERED, CANCELLED -> false; // terminal states
    };
  }
}
