package com.company.logicstic.shared.exception;

/** Exception for bad request scenarios (invalid input). */
public class BadRequestException extends ApiException {

  public BadRequestException(String message) {
    super(ErrorCode.BAD_REQUEST, message);
  }

  public BadRequestException(String code, String message) {
    super(ErrorCode.BAD_REQUEST.getStatus(), code, message);
  }
}
