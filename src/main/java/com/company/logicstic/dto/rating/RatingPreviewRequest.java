package com.company.logicstic.dto.rating;

import java.util.List;
import java.util.UUID;

/** Explicit accounting request dimensions; customer/date always come from persisted Load. */
public record RatingPreviewRequest(UUID contractId, @jakarta.validation.constraints.Positive Integer contractVersion, String lane, String equipment,
        String service, String tier, @jakarta.validation.constraints.NotBlank String currency, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=2000) String contextSource,
        UUID linehaulMileageEvidenceId, UUID fscMileageEvidenceId, @jakarta.validation.constraints.NotNull List<@jakarta.validation.constraints.NotNull UUID> accessorialIds) {
    public RatingPreviewRequest {
        if (accessorialIds != null) accessorialIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(accessorialIds));
    }
}
