package com.company.logicstic.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for conflict scenarios (duplicate resources, state conflicts).
 */
public class ConflictException extends ApiException {

    /**
     * @deprecated Use {@link #forUniqueField(String, String)} for duplicate input
     * values, or the code/message constructor for command conflicts.
     */
    @Deprecated
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictException(String message, java.util.List<com.company.logicstic.dto.ApiError> errors) {
        super(HttpStatus.CONFLICT, "DUPLICATE_VALUE", message, errors, java.util.Map.of());
    }

    public static ConflictException forUniqueField(String field, String userMessage) {
        return new ConflictException(userMessage, java.util.List.of(
                new com.company.logicstic.dto.ApiError(field, "UNIQUE", "This value is already in use")));
    }

    public ConflictException(String code, String message, java.util.List<com.company.logicstic.dto.ApiError> errors) {
        super(HttpStatus.CONFLICT, code, message, errors, java.util.Map.of());
    }

    public ConflictException(String code, String message, java.util.List<com.company.logicstic.dto.ApiError> errors, java.util.Map<String, Object> params) {
        super(HttpStatus.CONFLICT, code, message, errors, params);
    }
}
