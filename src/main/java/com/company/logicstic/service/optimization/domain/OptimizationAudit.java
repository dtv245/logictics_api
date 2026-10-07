package com.company.logicstic.service.optimization.domain;

import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.service.optimization.OptimizationForecastResolver.Result;
import com.company.logicstic.service.optimization.OptimizationScoringEngine.Score;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import com.company.logicstic.service.optimization.domain.OptimizationQualifiedInput.Scope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OptimizationAudit {
    private OptimizationAudit() {}
    public record PublishedPolicy(UUID id, String code, int version, Policy eligibilitySourcePolicy,
                                  OptimizationScoringPolicy scoringPolicy, String approvalReference,
                                  UUID publishedBy, Instant publishedAt) {}
    public record Target(@jakarta.validation.constraints.NotNull UUID loadId, @jakarta.validation.constraints.NotNull UUID tripId, @jakarta.validation.constraints.NotNull UUID ratingSnapshotId, @jakarta.validation.constraints.NotNull UUID pickupStopId) {
        /** Historical audit deserialization/fixtures only; new commands require an explicit pickup stop. */
        public Target(UUID loadId, UUID tripId, UUID ratingSnapshotId) { this(loadId,tripId,ratingSnapshotId,null); }
    }
    public record SourceSelection(@jakarta.validation.constraints.NotNull @jakarta.validation.Valid Scope scope, @jakarta.validation.constraints.NotNull UUID capacityInputId, @jakarta.validation.constraints.NotNull UUID qualificationInputId, @jakarta.validation.constraints.NotNull UUID forecastInputId) {}
    public record RunRequest(String idempotencyKey, UUID policyId, List<Target> targets,
                             List<UUID> driverIds, List<UUID> truckIds, List<SourceSelection> sourceSelections, String tenantScope) {
        public RunRequest { targets = targets == null ? null : List.copyOf(targets);
            driverIds = driverIds == null ? null : List.copyOf(driverIds); truckIds = truckIds == null ? null : List.copyOf(truckIds);
            sourceSelections = sourceSelections == null ? null : List.copyOf(sourceSelections); }
        public RunRequest(String key, UUID policyId,List<Target> targets,List<UUID> drivers,List<UUID> trucks) {
            this(key,policyId,targets,drivers,trucks,null,null);
        }
    }
    public record PolicySnapshot(Policy eligibilitySourcePolicy, OptimizationScoringPolicy scoringPolicy) {}
    public record Run(UUID id, UUID policyId, Instant createdAt, Instant planningUntil, UUID createdBy,
                      String idempotencyKey, String normalizedInputHash, RunRequest requestSnapshot,
                      PolicySnapshot policySnapshot, Instant calculatedAt, long durationMillis, String correlationId) {}
    public record Explanation(CandidateContext context, Facts facts, Bundle evidence, Input<Assessment> hos,
                              Result forecast, boolean feasible, List<String> rejectionCodes, Score score, String databaseStateFingerprint) {
        public Explanation { rejectionCodes = List.copyOf(rejectionCodes); }
        public Explanation(CandidateContext context,Facts facts,Bundle evidence,Input<Assessment> hos,Result forecast,boolean feasible,List<String> rejectionCodes,Score score) {
            this(context,facts,evidence,hos,forecast,feasible,rejectionCodes,score,null);
        }
    }
    public record Candidate(UUID id, UUID runId, UUID loadId, UUID tripId, UUID driverId, UUID truckId,
                            UUID ratingSnapshotId, boolean feasible, List<String> rejectionCodes, BigDecimal finalScore,
                            Integer rank, String inputFingerprint, Explanation explanation) {
        public Candidate { rejectionCodes = List.copyOf(rejectionCodes); }
    }
    public record Outcome(Run run, List<Candidate> candidates) { public Outcome { candidates = List.copyOf(candidates); } }
}
