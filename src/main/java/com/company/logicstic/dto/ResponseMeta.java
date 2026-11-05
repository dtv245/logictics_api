package com.company.logicstic.dto;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

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
        String requestId,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        PageInfo pagination
) {
    public ResponseMeta(Instant timestamp, String path, String requestId) {
        this(timestamp, path, requestId, null);
    }

    public static ResponseMeta from(HttpServletRequest request) {
        return new ResponseMeta(
                Instant.now(),
                request.getRequestURI(),
                com.company.logicstic.config.RequestIds.getOrCreate(request)
        );
    }

    public static ResponseMeta from(HttpServletRequest request, Page<?> page) {
        return from(request, page, true);
    }

    public static ResponseMeta from(HttpServletRequest request, Page<?> page, boolean oneBased) {
        return new ResponseMeta(
                Instant.now(),
                request.getRequestURI(),
                com.company.logicstic.config.RequestIds.getOrCreate(request),
                oneBased ? PageInfo.ofOneBased(page) : PageInfo.of(page)
        );
    }
}
