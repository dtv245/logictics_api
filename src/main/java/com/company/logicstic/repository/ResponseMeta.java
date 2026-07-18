package com.company.logicstic.repository;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;

public record ResponseMeta(
        Instant timestamp,
        String path,
        String requestId
) {
    public static ResponseMeta from(HttpServletRequest request) {
        return new ResponseMeta(
                Instant.now(),
                request.getRequestURI(),
                request.getHeader("X-Request-Id")
        );
    }
}
