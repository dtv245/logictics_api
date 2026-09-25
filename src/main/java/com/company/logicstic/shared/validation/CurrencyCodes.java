package com.company.logicstic.shared.validation;

import java.util.Locale;

/** Canonicalizes a validated ISO currency code for persistence and API responses. */
public final class CurrencyCodes {

  private CurrencyCodes() {}

  public static String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }
}
