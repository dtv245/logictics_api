package com.company.logicstic.dto.rating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RatingAcceptRequest(
        @NotBlank String idempotencyKey,
        @NotNull @jakarta.validation.Valid RatingPreviewRequest rating,
        @NotBlank String expectedInputHash,
        @NotBlank String expectedResultHash,
        UUID supersedesSnapshotId,
        String reasonCode,
        String reason
) { }
