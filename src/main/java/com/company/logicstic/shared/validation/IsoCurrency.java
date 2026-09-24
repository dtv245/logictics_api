package com.company.logicstic.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates an ISO 4217 currency code accepted by the JDK currency registry. */
@Documented
@Constraint(validatedBy = IsoCurrencyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsoCurrency {

  String message() default "must be a valid ISO 4217 currency code";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
