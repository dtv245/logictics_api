package com.company.logicstic.modules.terminal.enums;

import java.util.Arrays;

/**
 * Terminal categories per {@code docs/docs/business-spec.md} §2.4: sea port, rail terminal, inland
 * depot, air cargo facility, border crossing.
 *
 * <p>The {@code terminals."type"} column is {@code text} in the legacy .NET schema, so the stored
 * representation is declared explicitly instead of relying on {@link Enum#name()} or the ordinal.
 * Persisting by ordinal would silently rewrite history the day a constant is inserted in the
 * middle.
 */
public enum TerminalType {
  SEA_PORT("SeaPort"),
  RAIL_TERMINAL("RailTerminal"),
  INLAND_DEPOT("InlandDepot"),
  AIR_CARGO("AirCargo"),
  BORDER_CROSSING("BorderCrossing");

  private final String dbValue;

  TerminalType(String dbValue) {
    this.dbValue = dbValue;
  }

  /** Returns the value stored in the {@code type} column. */
  public String dbValue() {
    return dbValue;
  }

  /**
   * Parses a stored value back to an enum constant, accepting both the database representation
   * ({@code SeaPort}) and the constant name ({@code SEA_PORT}) so legacy rows and API payloads both
   * resolve.
   *
   * @return {@code null} when {@code value} is {@code null}
   * @throws IllegalArgumentException when the value is not a known terminal type
   */
  public static TerminalType fromDbValue(String value) {
    if (value == null) {
      return null;
    }
    return Arrays.stream(values())
        .filter(type -> type.dbValue.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown terminal type: " + value));
  }
}
