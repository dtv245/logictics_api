package com.company.logicstic.ai.dispatch.dto;

import com.company.logicstic.dto.PageInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AiDispatchDtos {

    public record RunRequest(
        @NotNull UUID loadId,
        String mode, // "HumanInTheLoop" (default), "Autonomous" (locked)
        Integer maxSuggestions,
        String instructions
    ) {}

    public record TokenUsage(
        int input,
        int output,
        int total,
        /** False when the numbers are placeholders, not provider-reported usage. */
        boolean measured
    ) {}

    public record SuggestionMetric(
        Double deadheadMiles,
        Double expectedMarginRatio,
        Double hosRemainingHours,
        Double onTimeProbability
    ) {}

    public record Suggestion(
        UUID decisionId,
        Integer rank,
        UUID truckId,
        String truckNumber,
        UUID driverId,
        String driverName,
        BigDecimal score,
        String confidence, // "HIGH", "MEDIUM", "LOW"
        String reasoning,
        SuggestionMetric metrics,
        String status
    ) {}

    public record RunResult(
        UUID sessionId,
        Long sessionNumber,
        String status,
        String runStatus,
        String resultCode,
        String mode,
        String rankingSource, // "evidence_unavailable" until qualified inputs are connected
        String modelUsed,
        TokenUsage tokensUsed,
        BigDecimal estimatedCostUsd,
        String summary,
        List<Suggestion> suggestions
    ) {}

    public record ApproveRequest(
        String notes
    ) {}

    public record RejectRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {}

    public record SessionDetail(
        UUID id,
        Long number,
        String mode,
        String status,
        UUID triggeredByUserId,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String modelUsed,
        Integer decisionCount,
        BigDecimal estimatedCostUsd,
        String summary,
        String errorMessage,
        List<Suggestion> decisions,
        PageInfo decisionsPagination
    ) {
        public SessionDetail(UUID id, Long number, String mode, String status, UUID triggeredByUserId,
                OffsetDateTime startedAt, OffsetDateTime completedAt, String modelUsed, Integer decisionCount,
                BigDecimal estimatedCostUsd, String summary, String errorMessage, List<Suggestion> decisions) {
            this(id, number, mode, status, triggeredByUserId, startedAt, completedAt, modelUsed, decisionCount,
                    estimatedCostUsd, summary, errorMessage, decisions, null);
        }
    }

    public record ConfigView(
        boolean gapEnabled,
        boolean dispatchEnabled,
        boolean dryRun,
        String defaultProvider,
        String activeModel,
        int maxTokens,
        int cooldownMinutes,
        int maxAsks
    ) {}

    public record FeaturesView(
        boolean gapDetection,
        boolean dispatchOptimization,
        boolean dryRun,
        boolean autonomousModeLocked,
        String llmProvider,
        String activeModel,
        int maxTokens,
        int cooldownMinutes,
        int maxAsks
    ) {}

    public record LoadCapabilities(
        boolean canApprove,
        boolean canReject,
        boolean canRerun,
        boolean canViewRuns
    ) {}

    public record LoadStatusView(
        UUID loadId,
        String gapStatus, // "CLEAN", "HAS_BLOCKING", "HAS_WARNING"
        int blockingGapsCount,
        int warningGapsCount,
        String dispatchStatus, // "UNASSIGNED", "SUGGESTED", "EXECUTED"
        UUID latestSessionId,
        UUID latestDecisionId,
        UUID suggestedTruckId,
        String suggestedTruckNumber,
        LoadCapabilities capabilities,
        UUID latestRunId,
        String latestRunStatus,
        String latestRunResultCode,
        String latestRunSummary,
        OffsetDateTime latestRunAt
    ) {
        public LoadStatusView(UUID loadId, String gapStatus, int blockingGapsCount, int warningGapsCount,
                String dispatchStatus, UUID latestSessionId, UUID latestDecisionId, UUID suggestedTruckId,
                String suggestedTruckNumber, LoadCapabilities capabilities) {
            this(loadId, gapStatus, blockingGapsCount, warningGapsCount, dispatchStatus, latestSessionId,
                latestDecisionId, suggestedTruckId, suggestedTruckNumber, capabilities, null,
                null, null, null, null);
        }
    }

    public record OverviewRequest(
        @NotNull @Size(min = 1, max = 100) List<UUID> loadIds
    ) {}

    public record RerunRequest(
        @NotBlank String target // "gap", "dispatch", "both"
    ) {}

    public record RerunResult(
        UUID loadId,
        String target,
        Object gapResult,
        RunResult dispatchResult
    ) {}

    public record RunAuditView(
        UUID id,
        String runType,
        String status,
        UUID loadId,
        String modelName,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal estimatedCostUsd,
        long latencyMs,
        String errorMessage,
        String resultSummary,
        OffsetDateTime createdAt
    ) {}
}
