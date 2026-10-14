package com.company.logicstic.exception;

import com.company.logicstic.config.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.slf4j.Logger;

/** Logs actionable stack locations without SQL rows, credentials or rejected input. */
public final class ErrorLog {
    private ErrorLog() {}
    public static void write(Logger log, int status, String code, Throwable error, HttpServletRequest request) {
        String path = request.getRequestURI().replaceAll("[\\r\\n]", "");
        path = path.substring(0, Math.min(path.length(), 1000));
        if (status >= 500) log.error("api_failure status={} code={} method={} path={} requestId={}",
                status, code, request.getMethod(), path, RequestIds.getOrCreate(request), sanitized(error));
        else log.info("api_failure status={} code={} method={} path={} requestId={}",
                status, code, request.getMethod(), path, RequestIds.getOrCreate(request));
    }
    public static Throwable sanitized(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return sanitized(error, seen, 0);
    }
    private static Throwable sanitized(Throwable error, Set<Throwable> seen, int depth) {
        if (error == null || depth >= 8 || !seen.add(error)) return null;
        var safe = new RuntimeException(error.getClass().getName(), sanitized(error.getCause(), seen, depth + 1));
        safe.setStackTrace(error.getStackTrace());
        return safe;
    }
}
