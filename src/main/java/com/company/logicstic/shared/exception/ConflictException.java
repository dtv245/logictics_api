package com.company.logicstic.shared.exception;

/** Exception for conflict scenarios (duplicate resources, state conflicts). */
public class ConflictException extends ApiException {

  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, message);
  }

  public ConflictException(String code, String message) {
    super(ErrorCode.CONFLICT.getStatus(), code, message);
  }
}
