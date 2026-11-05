package com.company.logicstic.ai.event;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record LoadChangedInternalEvent(
    UUID loadId,
    String tenantId,
    String source, // "LARK_BASE", "TMS"
    Set<String> changedFields,
    Instant timestamp
) {
    public LoadChangedInternalEvent {
        changedFields = changedFields == null ? Set.of() : Set.copyOf(changedFields);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
