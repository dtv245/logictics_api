package com.company.logicstic.shared.exception;

import com.company.logicstic.shared.dto.ApiError;
import com.company.logicstic.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(
      ApiException exception, HttpServletRequest request) {
    ApiResponse<Void> body =
        ApiResponse.failure(exception.getCode(), exception.getMessage(), List.of(), request);
    return ResponseEntity.status(exception.getStatus()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<ApiError> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error -> new ApiError(error.getField(), error.getCode(), error.getDefaultMessage()))
            .toList();
    return badRequest(ErrorCode.VALIDATION_FAILED, errors, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    List<ApiError> errors =
        exception.getConstraintViolations().stream()
            .map(
                violation ->
                    new ApiError(
                        violation.getPropertyPath().toString(),
                        violation
                            .getConstraintDescriptor()
                            .getAnnotation()
                            .annotationType()
                            .getSimpleName(),
                        violation.getMessage()))
            .toList();
    return badRequest(ErrorCode.VALIDATION_FAILED, errors, request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    log.warn("Unreadable request body for {} {}", request.getMethod(), request.getRequestURI());
    return badRequest(ErrorCode.MALFORMED_REQUEST, List.of(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
      DataIntegrityViolationException exception, HttpServletRequest request) {
    log.warn(
        "Data integrity violation for {} {}",
        request.getMethod(),
        request.getRequestURI(),
        exception);
    return failure(ErrorCode.DATA_INTEGRITY_VIOLATION, request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
      NoResourceFoundException exception, HttpServletRequest request) {
    return failure(ErrorCode.NOT_FOUND, "Resource not found: " + request.getRequestURI(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {
    log.error(
        "Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
    return failure(ErrorCode.INTERNAL_ERROR, request);
  }

  private static ResponseEntity<ApiResponse<Void>> badRequest(
      ErrorCode errorCode, List<ApiError> errors, HttpServletRequest request) {
    return ResponseEntity.status(errorCode.getStatus())
        .body(
            ApiResponse.failure(errorCode.code(), errorCode.getDefaultMessage(), errors, request));
  }

  private static ResponseEntity<ApiResponse<Void>> failure(
      ErrorCode errorCode, HttpServletRequest request) {
    return failure(errorCode, errorCode.getDefaultMessage(), request);
  }

  private static ResponseEntity<ApiResponse<Void>> failure(
      ErrorCode errorCode, String message, HttpServletRequest request) {
    return ResponseEntity.status(errorCode.getStatus())
        .body(ApiResponse.failure(errorCode.code(), message, List.of(), request));
  }
}
