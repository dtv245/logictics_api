package com.company.logicstic.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every failure this API reports deliberately.
 *
 * <p>{@code GlobalExceptionHandler} maps any subclass straight onto the response envelope using
 * {@link #getStatus()} and {@link #getCode()}, so a service never builds a {@code ResponseEntity}
 * and never throws a raw {@code RuntimeException} for a business failure
 * (docs/docs/development/engineering-conventions.md §8).
 *
 * <p>Prefer the {@link ErrorCode} constructors: they keep the status and the wire code together in
 * one catalogue. The explicit-string constructor remains for a feature-specific code such as {@code
 * TERMINAL_IN_USE} that is not worth a global constant.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  /** Uses the status and the default message carried by the {@link ErrorCode}. */
  public ApiException(ErrorCode errorCode) {
    this(errorCode, errorCode.getDefaultMessage());
  }

  /** Uses the status of the {@link ErrorCode} with a message specific to this throw site. */
  public ApiException(ErrorCode errorCode, String message) {
    super(message);
    this.status = errorCode.getStatus();
    this.code = errorCode.code();
  }

  /** Escape hatch for a feature-specific code that has no global {@link ErrorCode}. */
  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
