package com.company.logicstic.dto;

public record ApiError(
        String field,
        String code,
        String message,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        java.util.Map<String, Object> params
) {
    public ApiError { params = params == null ? java.util.Map.of() : java.util.Map.copyOf(params); }
    public ApiError(String field, String code, String message) { this(field, code, message, java.util.Map.of()); }
}
