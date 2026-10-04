package com.company.logicstic.dto.load;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record RecordLoadEventRequest(
        @NotBlank String eventType,
        String previousStatus,
        String newStatus,
        OffsetDateTime occurredAt,
        Double latitude,
        Double longitude,
        String source,
        UUID actorId,
        UUID tripId,
        UUID tripStopId,
        UUID documentId,
        String note
) {}
