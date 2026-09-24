package com.company.logicstic.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Currency;

public class IsoCurrencyValidator implements ConstraintValidator<IsoCurrency, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    try {
      Currency.getInstance(value.trim().toUpperCase(java.util.Locale.ROOT));
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
