package com.company.logicstic.dto;

public record ApiError(
        String field,
        String code,
        String message
) {
}
