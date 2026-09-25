package com.company.logicstic.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IsoCurrencyValidatorTest {

  private final IsoCurrencyValidator validator = new IsoCurrencyValidator();

  @Test
  void acceptsIso4217CodesCaseInsensitively() {
    assertThat(validator.isValid("usd", null)).isTrue();
    assertThat(validator.isValid("VND", null)).isTrue();
  }

  @Test
  void rejectsUnknownCurrencyCodes() {
    assertThat(validator.isValid("not-a-currency", null)).isFalse();
  }

  @Test
  void normalizesCurrencyForPersistence() {
    assertThat(CurrencyCodes.normalize(" usd ")).isEqualTo("USD");
  }
}
