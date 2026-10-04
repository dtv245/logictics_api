package com.company.logicstic.service.rating.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AcceptedRatingSnapshot(UUID snapshotId, RatingPreview calculation,
        UUID acceptedBy, OffsetDateTime acceptedAt, UUID supersedesSnapshotId,
        String reasonCode, String reason, String idempotencyKey, String commandHash) { }
