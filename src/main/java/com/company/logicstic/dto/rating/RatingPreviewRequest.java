package com.company.logicstic.dto.rating;

import java.util.List;
import java.util.UUID;

/** Explicit accounting request dimensions; customer/date always come from persisted Load. */
public record RatingPreviewRequest(UUID contractId, Integer contractVersion, String lane, String equipment,
        String service, String tier, String currency, String contextSource,
        UUID linehaulMileageEvidenceId, UUID fscMileageEvidenceId, List<UUID> accessorialIds) {
    public RatingPreviewRequest {
        if (accessorialIds != null) accessorialIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(accessorialIds));
    }
}
