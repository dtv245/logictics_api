package com.company.logicstic.service.optimization.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Qualified, candidate-bound inputs; not a public API for callers to assert feasibility. */
public final class OptimizationEvidence {
    private OptimizationEvidence() {}
    public enum Kind { VEHICLE_LOCATION, DRIVER_AVAILABILITY, TRUCK_AVAILABILITY, ROUTE, CAPACITY, QUALIFICATION, HOS, FORECAST_COST }
    public enum EntityKind { LOAD, TRIP, DRIVER, TRUCK }
    public record CandidateContext(UUID loadId, UUID tripId, UUID driverId, UUID truckId,
                                   Instant planningStart, Instant planningEnd) {}
    public enum SourceClass { AUTHORITATIVE_DB, TRUSTED_ADAPTER }
    public record Source(String type, String reference, String version, SourceClass classification) {}
    public record Provenance(Source source, CandidateContext context, String evidenceReference,
                             String evidenceVersion, String unit, Instant observedAt, Instant expiresAt,
                             Long maxAgeSeconds) {}
    public record Input<T>(T value, Provenance provenance) {}
    public record Distance(BigDecimal originalValue, String originalUnit, BigDecimal normalizedMiles) {}
    public record Weight(BigDecimal originalValue, String originalUnit, BigDecimal normalizedPounds) {}
    public record Location(BigDecimal latitude, BigDecimal longitude) {}
    public record Availability(boolean available, Instant coversFrom, Instant coversUntil) {}
    public record Capacity(Weight cargo, Weight truck) {}
    public record Qualification(boolean qualifiedDriver, boolean validLicense, boolean hazmatDriver,
                                boolean equipmentMatches, boolean hazmatTruck, boolean operational,
                                boolean maintenanceClear, boolean hazmatRequired,
                                Instant effectiveFrom, Instant effectiveUntil) {}
    public record Route(Distance deadhead, Distance loadAttributedLoadedMiles, Instant appointmentStart,
                        Instant predictedArrivalAtPickup, boolean pickupReachable,
                        String simulatedRoutePlanReference, String simulatedRoutePlanVersion) {}
    public record Facts(Map<EntityKind, String> statuses, LocalDate requestedPickupBusinessDate,
                        UUID acceptedRatingSnapshotId, boolean sameTenantAndTripLoadContext,
                        boolean loadPreDispatch, boolean tripPreDispatch, boolean routingLocationsAvailable,
                        boolean conflictingActiveAssignment) {
        public Facts { statuses = statuses == null ? Map.of() : Map.copyOf(statuses); }
    }
    public record Bundle(Input<Location> location, Input<Availability> driverAvailability,
                         Input<Availability> truckAvailability, Input<Capacity> capacity,
                         Input<Qualification> qualification, Input<Route> route) {}
    public record Policy(String eligibilityCode, int eligibilityVersion, int planningHorizonHours,
                         String sourceCode, int sourceVersion,
                         Map<EntityKind, Set<String>> statusAllowlists, Map<Kind, Set<Source>> qualifiedSources) {
        public Policy {
            if (!"OPT_ELIGIBILITY_V1".equals(eligibilityCode) || eligibilityVersion != 1 || planningHorizonHours != 72
                    || !"OPT_SOURCE_V1".equals(sourceCode) || sourceVersion != 1
                    || statusAllowlists == null || qualifiedSources == null) {
                throw invalid("Explicit confirmed eligibility/source policy is required");
            }
            var statuses = new java.util.EnumMap<EntityKind, Set<String>>(EntityKind.class);
            for (EntityKind kind : EntityKind.values()) {
                Set<String> values = statusAllowlists.get(kind);
                if (values == null || values.isEmpty() || values.stream().anyMatch(v -> v == null || v.isBlank() || !v.equals(v.trim()))) {
                    throw invalid("Exact authored nonempty status allowlists required for each entity");
                }
                statuses.put(kind, Set.copyOf(values));
            }
            var sources = new java.util.EnumMap<Kind, Set<Source>>(Kind.class);
            for (Kind kind : Kind.values()) {
                Set<Source> values = qualifiedSources.get(kind);
                if (values == null || values.isEmpty() || values.stream().anyMatch(s -> s == null
                        || blank(s.type()) || blank(s.reference()) || blank(s.version()) || s.classification() == null
                        || kind == Kind.QUALIFICATION && s.classification() != SourceClass.AUTHORITATIVE_DB)) {
                    throw invalid("Explicit qualified source/version registrations required for every input kind");
                }
                sources.put(kind, Set.copyOf(values));
            }
            statusAllowlists = Map.copyOf(statuses);
            qualifiedSources = Map.copyOf(sources);
        }
    }
    public static boolean blank(String value) { return value == null || value.isBlank(); }
    private static com.company.logicstic.exception.BadRequestException invalid(String message) {
        return new com.company.logicstic.exception.BadRequestException("INVALID_OPTIMIZATION_POLICY", message);
    }
}
