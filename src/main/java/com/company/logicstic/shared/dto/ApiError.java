package com.company.logicstic.shared.dto;

public record ApiError(
        String field,
        String code,
        String message
) {
}
