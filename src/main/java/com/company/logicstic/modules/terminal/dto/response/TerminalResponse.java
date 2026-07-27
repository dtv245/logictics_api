package com.company.logicstic.modules.terminal.dto.response;

import com.company.logicstic.modules.terminal.enums.TerminalType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model returned by the terminal endpoints.
 *
 * <p>Deliberately a separate type from {@link
 * com.company.logicstic.modules.terminal.dto.request.CreateTerminalRequest}: this record exposes
 * server-generated fields ({@code id}, audit timestamps) that a client must never send. Reusing one
 * record for both directions is how write-protected fields become writable.
 */
public record TerminalResponse(
    UUID id,
    String name,
    String code,
    String countryCode,
    TerminalType type,
    String notes,
    String addressLine1,
    String addressLine2,
    String addressCity,
    String addressState,
    String addressZipCode,
    String addressCountry,
    OffsetDateTime createdAt,
    OffsetDateTime lastModifiedAt) {}
