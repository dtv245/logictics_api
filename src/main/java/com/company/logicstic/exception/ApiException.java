package com.company.logicstic.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final java.util.List<com.company.logicstic.dto.ApiError> errors;
    private final java.util.Map<String, Object> params;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, java.util.List.of(), java.util.Map.of());
    }

    public ApiException(HttpStatus status, String code, String message,
            java.util.List<com.company.logicstic.dto.ApiError> errors, java.util.Map<String, Object> params) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = java.util.List.copyOf(errors);
        this.params = java.util.Map.copyOf(params);
    }

    public java.util.List<com.company.logicstic.dto.ApiError> getErrors() { return errors; }
    public java.util.Map<String, Object> getParams() { return params; }
    public org.springframework.http.HttpHeaders getHeaders() { return new org.springframework.http.HttpHeaders(); }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
