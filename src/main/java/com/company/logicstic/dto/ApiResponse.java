package com.company.logicstic.dto;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        List<ApiError> errors,
        ResponseMeta meta,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        java.util.Map<String, Object> params
) {
    public ApiResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        params = params == null ? null : java.util.Map.copyOf(params);
    }

    public ApiResponse(boolean success, String code, String message, T data, List<ApiError> errors, ResponseMeta meta) {
        this(success, code, message, data, errors, meta, success ? null : java.util.Map.of());
    }

    public static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return success("OK", "Request completed successfully", data, request);
    }

    public static <T> ApiResponse<T> success(
            String code,
            String message,
            T data,
            HttpServletRequest request
    ) {
        return new ApiResponse<>(true, code, message, data, List.of(), ResponseMeta.from(request));
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message,
            List<ApiError> errors,
            HttpServletRequest request
    ) {
        return new ApiResponse<>(false, code, message, null, errors, ResponseMeta.from(request));
    }

    public static <T> ApiResponse<T> failure(String code, String message, List<ApiError> errors,
            java.util.Map<String, Object> params, HttpServletRequest request) {
        return new ApiResponse<>(false, code, message, null, errors, ResponseMeta.from(request), params);
    }
}
