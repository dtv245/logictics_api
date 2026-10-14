package com.company.logicstic.common;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Compare the version the caller read; never assign a client version to an entity. */
public final class ExpectedVersionGuard {
    private ExpectedVersionGuard() {}

    public static void require(Long expected, Long current) {
        if (expected == null || expected < 0)
            throw new BadRequestException("EXPECTED_VERSION_REQUIRED", "A nonnegative expectedVersion is required");
        if (!Objects.equals(expected, current))
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "The record changed since it was read. Reload it before updating.",
                    List.of(), Map.of("conflictType", "VERSION", "legacyCode", "CONCURRENT_MODIFICATION_CONFLICT"));
    }
}
