package com.company.logicstic.dto.load;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoadEventView(
        UUID id,
        UUID loadId,
        UUID tripId,
        UUID tripStopId,
        String eventType,
        String previousStatus,
        String newStatus,
        OffsetDateTime occurredAt,
        Double latitude,
        Double longitude,
        String source,
        UUID actorId,
        UUID documentId,
        String note
) {}
