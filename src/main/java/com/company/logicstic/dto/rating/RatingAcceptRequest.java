package com.company.logicstic.dto.rating;

import java.util.UUID;

public record RatingAcceptRequest(String idempotencyKey, RatingPreviewRequest rating,
        String expectedInputHash, String expectedResultHash, UUID supersedesSnapshotId,
        String reasonCode, String reason) { }
