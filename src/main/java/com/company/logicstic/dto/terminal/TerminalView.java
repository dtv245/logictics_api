package com.company.logicstic.dto.terminal;

import com.company.logicstic.entity.Terminal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TerminalView(
        UUID id,
        String name,
        String code,
        String countryCode,
        String type,
        String notes,
        String addressCity,
        String addressCountry,
        String addressLine1,
        String addressLine2,
        String addressState,
        String addressZipCode,
        OffsetDateTime createdAt,
        OffsetDateTime lastModifiedAt
) {
    public static TerminalView from(Terminal t) {
        if (t == null) {
            return null;
        }
        return new TerminalView(
                t.getId(),
                t.getName(),
                t.getCode(),
                t.getCountryCode(),
                t.getType(),
                t.getNotes(),
                t.getAddressCity(),
                t.getAddressCountry(),
                t.getAddressLine1(),
                t.getAddressLine2(),
                t.getAddressState(),
                t.getAddressZipCode(),
                t.getCreatedAt(),
                t.getLastModifiedAt()
        );
    }
}
