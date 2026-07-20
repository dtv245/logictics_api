package com.company.logicstic.shared.dto;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public record ApiResponse<T>(
    boolean success,
    String code,
    String message,
    T data,
    List<ApiError> errors,
    ResponseMeta meta) {
  public ApiResponse {
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
    return success("OK", "Request completed successfully", data, request);
  }

  public static <T> ApiResponse<T> success(
      String code, String message, T data, HttpServletRequest request) {
    return new ApiResponse<>(true, code, message, data, List.of(), ResponseMeta.from(request));
  }

  public static <T> ApiResponse<T> failure(
      String code, String message, List<ApiError> errors, HttpServletRequest request) {
    return new ApiResponse<>(false, code, message, null, errors, ResponseMeta.from(request));
  }
}
