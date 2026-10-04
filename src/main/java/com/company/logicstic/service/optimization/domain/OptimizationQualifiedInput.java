package com.company.logicstic.service.optimization.domain;

import com.company.logicstic.service.optimization.OptimizationForecastResolver.Cost;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append-only authoritative evidence; scope is a real candidate, not a Trip allocation heuristic. */
public final class OptimizationQualifiedInput {
    private OptimizationQualifiedInput() {}
    public record Scope(UUID loadId, UUID tripId, UUID driverId, UUID truckId) {
        public boolean matches(CandidateContext c) {
            return c != null && java.util.Objects.equals(loadId, c.loadId()) && java.util.Objects.equals(tripId, c.tripId())
                    && java.util.Objects.equals(driverId, c.driverId()) && java.util.Objects.equals(truckId, c.truckId());
        }
    }
    public record Forecast(boolean accessorialApplicable, boolean permitApplicable, List<Cost> costs) {
        public Forecast { costs = List.copyOf(costs); }
    }
    public record Payload(Capacity capacity, Qualification qualification, Forecast forecast) {}
    public record Captured(UUID id, UUID policyId, Kind kind, Scope scope, Source source, String unit,
                           Instant observedAt, Instant expiresAt, Long maxAgeSeconds, int evidenceVersion,
                           UUID supersedesInputId, Payload payload, String approvalReference, String reasonCode,
                           String reason, UUID capturedBy, Instant capturedAt, String normalizedInputHash) {
        public <T> Input<T> bind(T value, CandidateContext context) {
            if (!scope.matches(context)) throw new com.company.logicstic.exception.BadRequestException(
                    "OPTIMIZATION_INPUT_EVIDENCE_INVALID", "Qualified input must belong to this exact candidate");
            return new Input<>(value, new Provenance(source, context, id.toString(), Integer.toString(evidenceVersion),
                    unit, observedAt, expiresAt, maxAgeSeconds));
        }
    }
}
