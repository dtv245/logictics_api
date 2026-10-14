package com.company.logicstic.dto;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Metadata included in every API response for traceability and debugging.
 *
 * @param timestamp The server time when the response was generated
 * @param path      The request URI that generated this response
 * @param requestId Validated correlation ID, generated when the caller does not supply one
 */
public record ResponseMeta(
        Instant timestamp,
        String path,
        String requestId
) {
    public static ResponseMeta from(HttpServletRequest request) {
        return new ResponseMeta(
                Instant.now(),
                request.getRequestURI(),
                com.company.logicstic.config.RequestIds.getOrCreate(request)
        );
    }
}
