package com.company.logicstic.shared.exception;

import org.springframework.http.HttpStatus;

/** Exception for conflict scenarios (duplicate resources, state conflicts). */
public class ConflictException extends ApiException {

  public ConflictException(String message) {
    super(HttpStatus.CONFLICT, "CONFLICT", message);
  }

  public ConflictException(String code, String message) {
    super(HttpStatus.CONFLICT, code, message);
  }
}
