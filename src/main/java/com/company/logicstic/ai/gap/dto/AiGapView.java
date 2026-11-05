package com.company.logicstic.ai.gap.dto;

import com.company.logicstic.dto.PageInfo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AiGapView(
    UUID id,
    UUID loadId,
    String ruleCode,
    String fieldName,
    String severity,
    String status,
    String questionTemplate,
    String questionNatural,
    String proposedValue,
    int askCount,
    int maxAsks,
    OffsetDateTime lastAskedAt,
    OffsetDateTime resolvedAt,
    OffsetDateTime dismissedAt,
    String dismissReason,
    OffsetDateTime createdAt
) {
    public record Summary(
        UUID loadId,
        Long loadNumber,
        boolean hasBlockingGaps,
        long blockingCount,
        long warningCount,
        List<AiGapView> gaps,
        PageInfo pagination
    ) {
        public Summary(UUID loadId, Long loadNumber, boolean hasBlockingGaps, long blockingCount,
                       long warningCount, List<AiGapView> gaps) {
            this(loadId, loadNumber, hasBlockingGaps, blockingCount, warningCount, gaps, null);
        }
    }
}
