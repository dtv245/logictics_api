package com.company.logicstic.service.optimization;

import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationEvidenceTest {
    final OptimizationEvidenceValidator validator = new OptimizationEvidenceValidator();
    final OptimizationHardFeasibilityService feasibility = new OptimizationHardFeasibilityService(validator);
    final Instant now = Instant.parse("2026-10-05T00:00:00Z");
    final CandidateContext context = new CandidateContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(72 * 3600));
    final Source source = new Source("QUALIFIED_FIXTURE", "fixture-registration", "v1", SourceClass.TRUSTED_ADAPTER);
    final Source databaseSource = new Source("QUALIFIED_DB_FIXTURE", "fixture-db-registration", "v1", SourceClass.AUTHORITATIVE_DB);
    final Policy policy = policy();
    static BigDecimal d(String s) { return new BigDecimal(s); }
    Policy policy() {
        var statuses = new EnumMap<EntityKind, Set<String>>(EntityKind.class);
        for (EntityKind kind : EntityKind.values()) statuses.put(kind, Set.of("EXPLICIT_APPROVED_TEST_STATE"));
        var sources = new EnumMap<Kind, Set<Source>>(Kind.class);
        for (Kind kind : Kind.values()) sources.put(kind, kind == Kind.QUALIFICATION ? Set.of(databaseSource) : Set.of(source, databaseSource));
        return new Policy("OPT_ELIGIBILITY_V1", 1, 72, "OPT_SOURCE_V1", 1, statuses, sources);
    }
    <T> Input<T> input(T value, String unit) { return input(value, unit, now, now.plusSeconds(600), null); }
    <T> Input<T> input(T value, String unit, Instant asOf, Instant expiresAt, Long maxAge) {
        return new Input<>(value, new Provenance("QUALIFICATION".equals(unit) ? databaseSource : source, context, "evidence", "revision-1", unit, asOf, expiresAt, maxAge));
    }
    Facts facts() {
        var statuses = new EnumMap<EntityKind, String>(EntityKind.class);
        for (EntityKind kind : EntityKind.values()) statuses.put(kind, "EXPLICIT_APPROVED_TEST_STATE");
        return new Facts(statuses, LocalDate.of(2026, 10, 5), UUID.randomUUID(), true, true, true, true, false);
    }
    Qualification qualification() { return new Qualification(true, true, true, true, true, true, true, true, now, context.planningEnd()); }
    Route route() { return new Route(validator.distance(d("10"), "MILE"), validator.distance(d("100"), "MILE"), now.plusSeconds(7200), now.plusSeconds(3600), true, "full-route-plan", "plan-v1"); }
    Bundle bundle() { return new Bundle(input(new Location(d("10"), d("106")), "WGS84"),
            input(new Availability(true, now, context.planningEnd()), "INTERVAL"), input(new Availability(true, now, context.planningEnd()), "INTERVAL"),
            input(new Capacity(validator.weight(d("1000"), "POUND"), validator.weight(d("2000"), "POUND")), "POUND"),
            input(qualification(), "QUALIFICATION"), input(route(), "MILE")); }
    Input<Assessment> hos() { return input(new Assessment("QUALIFIED_HOS_RULESET", "rules-v1", "full-route-plan", "plan-v1", true, true, true, true, true, true, d(".2")), "RATIO"); }
    OptimizationHardFeasibilityService.Result evaluate(Facts f, Bundle b, Input<Assessment> h) { return feasibility.evaluate(context, f, b, h, policy, now); }
    void rejects(String code, Facts f, Bundle b, Input<Assessment> h) { var result = evaluate(f, b, h); assertFalse(result.feasible()); assertTrue(result.rejectionCodes().contains(code), result.rejectionCodes().toString()); }
    Bundle availability(Input<Availability> driver, Input<Availability> truck) { Bundle b = bundle(); return new Bundle(b.location(), driver, truck, b.capacity(), b.qualification(), b.route()); }
    Bundle qualification(Qualification q) { Bundle b = bundle(); return new Bundle(b.location(), b.driverAvailability(), b.truckAvailability(), b.capacity(), input(q, "QUALIFICATION"), b.route()); }

    @Test void completeQualifiedCandidatePassesAllHardGates() { assertTrue(evaluate(facts(), bundle(), hos()).feasible()); }
    @Test void absentEvidenceIsNeverAssumedFeasible() { rejects("OPTIMIZATION_INPUT_EVIDENCE_REQUIRED", facts(), null, hos()); }
    @Test void unknownOrMissingStatusFailsClosedWithoutActiveDefault() {
        Facts f = facts();
        rejects("OPTIMIZATION_STATUS_NOT_ALLOWED", new Facts(Map.of(), f.requestedPickupBusinessDate(), f.acceptedRatingSnapshotId(), true, true, true, true, false), bundle(), hos());
        rejects("OPTIMIZATION_STATUS_NOT_ALLOWED", new Facts(Map.of(EntityKind.LOAD, "ACTIVE"), f.requestedPickupBusinessDate(), f.acceptedRatingSnapshotId(), true, true, true, true, false), bundle(), hos());
    }
    @Test void terminalTripOrLoadIsNotEligibleEvenWithListedStatus() {
        Facts f = facts(); rejects("OPTIMIZATION_STATUS_NOT_ALLOWED", new Facts(f.statuses(), f.requestedPickupBusinessDate(), f.acceptedRatingSnapshotId(), true, false, false, true, false), bundle(), hos());
    }
    @Test void contextDateRatingAndAssignmentReasonsAreAllRetained() {
        var f = new Facts(facts().statuses(), null, null, false, true, true, false, true);
        var result = evaluate(f, bundle(), hos());
        assertTrue(result.rejectionCodes().containsAll(List.of("RATING_PRICING_DATE_REQUIRED", "OPTIMIZATION_ACCEPTED_RATING_REQUIRED", "OPTIMIZATION_CONTEXT_MISMATCH", "OPTIMIZATION_ROUTING_CONTEXT_REQUIRED", "OPTIMIZATION_ASSIGNMENT_CONFLICT")));
    }
    @Test void truckUnavailableOrIntervalNotCoveredRejects() { rejects("TRUCK_UNAVAILABLE", facts(), availability(bundle().driverAvailability(), input(new Availability(false, now, context.planningEnd()), "INTERVAL")), hos()); }
    @Test void driverMustCoverWholePlanningInterval() { rejects("DRIVER_UNAVAILABLE", facts(), availability(input(new Availability(true, now, context.planningEnd().minusSeconds(1)), "INTERVAL"), bundle().truckAvailability()), hos()); }
    @Test void capacityUsesExplicitUnitsAndRejectsOverload() {
        Bundle b = bundle(); Bundle overload = new Bundle(b.location(), b.driverAvailability(), b.truckAvailability(), input(new Capacity(validator.weight(d("3000"), "POUND"), validator.weight(d("2000"), "POUND")), "POUND"), b.qualification(), b.route());
        rejects("CAPACITY_INFEASIBLE", facts(), overload, hos());
    }
    @Test void unqualifiedBareNumericCapacityCannotBecomePounds() { assertThrows(com.company.logicstic.exception.ApiException.class, () -> validator.weight(d("1000"), null)); }
    @Test void kilogramsAndKilometersConvertExplicitlyRetainingOriginalFacts() {
        Distance distance = validator.distance(d("1.609344"), "KILOMETER"); assertEquals(d("1.609344"), distance.originalValue()); assertEquals(0, BigDecimal.ONE.compareTo(distance.normalizedMiles()));
        Weight weight = validator.weight(d(".45359237"), "KILOGRAM"); assertEquals("KILOGRAM", weight.originalUnit()); assertEquals(0, BigDecimal.ONE.compareTo(weight.normalizedPounds()));
        assertFalse(validator.valid(new Distance(d("1.609344"), "KILOMETER", d("2"))));
    }
    @Test void qualificationEquipmentHazmatMaintenanceFailuresAllPersist() {
        Qualification q = new Qualification(false, false, false, false, false, false, false, true, now, context.planningEnd());
        var result = evaluate(facts(), qualification(q), hos());
        assertTrue(result.rejectionCodes().containsAll(List.of("DRIVER_QUALIFICATION_INFEASIBLE", "EQUIPMENT_INFEASIBLE", "HAZMAT_INFEASIBLE", "TRUCK_UNAVAILABLE", "MAINTENANCE_BLOCK")));
    }
    @Test void licenseAndQualificationMustRemainEffectiveForPlanningInterval() {
        Qualification q = new Qualification(true, true, true, true, true, true, true, true, now, context.planningEnd().minusSeconds(1));
        rejects("QUALIFICATION_EVIDENCE_UNAVAILABLE", facts(), qualification(q), hos());
    }
    @Test void pickupUnreachableIsRejectedBeforeRanking() {
        Bundle b = bundle(); Route r = route(); rejects("PICKUP_UNREACHABLE", facts(), new Bundle(b.location(), b.driverAvailability(), b.truckAvailability(), b.capacity(), b.qualification(), input(new Route(r.deadhead(), r.loadAttributedLoadedMiles(), r.appointmentStart(), r.predictedArrivalAtPickup(), false, r.simulatedRoutePlanReference(), r.simulatedRoutePlanVersion()), "MILE")), hos());
    }
    @Test void hosChecksDutyBreakCycleServiceAndNextAvailableNotOnlyDrive() {
        for (int failed = 0; failed < 6; failed++) {
            boolean[] checks = {true, true, true, true, true, true}; checks[failed] = false;
            rejects("HOS_INFEASIBLE", facts(), bundle(), input(new Assessment("RULESET", "1", "full-route-plan", "plan-v1", checks[0], checks[1], checks[2], checks[3], checks[4], checks[5], d(".2")), "RATIO"));
        }
    }
    @Test void hosPlanMismatchAndMissingHeadroomCannotBeScored() {
        rejects("HOS_EVIDENCE_CONTEXT_MISMATCH", facts(), bundle(), input(new Assessment("RULESET", "1", "different-plan", "plan-v1", true, true, true, true, true, true, d(".2")), "RATIO"));
        rejects("HOS_UTILITY_EVIDENCE_REQUIRED", facts(), bundle(), input(new Assessment("RULESET", "1", "full-route-plan", "plan-v1", true, true, true, true, true, true, null), "RATIO"));
    }
    @Test void locationAndHosAgeCapsCannotBeExtendedByProviderExpiry() {
        assertTrue(validator.validate(input(new Location(d("1"), d("1")), "WGS84", now.minusSeconds(301), now.plusSeconds(1000), null), Kind.VEHICLE_LOCATION, context, policy, now).contains("VEHICLE_LOCATION_STALE"));
        assertTrue(validator.validate(input(hos().value(), "RATIO", now.minusSeconds(301), now.plusSeconds(1000), null), Kind.HOS, context, policy, now).contains("OPTIMIZATION_INPUT_STALE"));
    }
    @Test void routeAgeUsesOwnFifteenMinutePolicy() {
        assertTrue(validator.validate(input(route(), "MILE", now.minusSeconds(900), now.plusSeconds(1000), null), Kind.ROUTE, context, policy, now).isEmpty());
        assertTrue(validator.validate(input(route(), "MILE", now.minusSeconds(901), now.plusSeconds(1000), null), Kind.ROUTE, context, policy, now).contains("ETA_FORECAST_STALE"));
    }
    @Test void sourceContextVersionTimestampAndValidityAreMandatory() {
        Input<Route> noSource = new Input<>(route(), new Provenance(new Source("UNREGISTERED", "x", "1", SourceClass.TRUSTED_ADAPTER), context, "e", "1", "MILE", now, now.plusSeconds(10), null));
        assertTrue(validator.validate(noSource, Kind.ROUTE, context, policy, now).contains("OPTIMIZATION_INPUT_EVIDENCE_INVALID"));
        assertTrue(validator.validate(input(route(), "MILE", null, now.plusSeconds(10), null), Kind.ROUTE, context, policy, now).contains("ETA_FORECAST_STALE"));
        assertTrue(validator.validate(input(route(), "MILE", now, null, null), Kind.ROUTE, context, policy, now).contains("OPTIMIZATION_INPUT_VALIDITY_REQUIRED"));
        assertTrue(validator.validate(input(route(), "MILE", now.plusSeconds(1), now.plusSeconds(10), null), Kind.ROUTE, context, policy, now).contains("ETA_FORECAST_STALE"));
        assertTrue(validator.validate(input(route(), "MILE"), Kind.ROUTE, new CandidateContext(UUID.randomUUID(), context.tripId(), context.driverId(), context.truckId(), now, context.planningEnd()), policy, now).contains("OPTIMIZATION_INPUT_EVIDENCE_INVALID"));
    }
    @Test void noGlobalTtlIsInventedForTruckAvailability() {
        var proof = input(bundle().truckAvailability().value(), "INTERVAL", now.minusSeconds(600), now.plusSeconds(1), null);
        assertTrue(validator.validate(proof, Kind.TRUCK_AVAILABILITY, context, policy, now).isEmpty());
        assertTrue(validator.validate(proof, Kind.TRUCK_AVAILABILITY, context, policy, now.plusSeconds(1)).contains("OPTIMIZATION_INPUT_STALE"));
    }
    @Test void driverDbSnapshotUsesSourceValidityWhileDynamicEvidenceHasFiveMinuteCap() {
        var value = bundle().driverAvailability().value();
        Provenance db = new Provenance(databaseSource, context, "db-assignment-snapshot", "db-v1", "INTERVAL", now.minusSeconds(600), now.plusSeconds(1), null);
        assertTrue(validator.validate(new Input<>(value, db), Kind.DRIVER_AVAILABILITY, context, policy, now).isEmpty());
        assertTrue(validator.validate(input(value, "INTERVAL", now.minusSeconds(600), now.plusSeconds(1), null), Kind.DRIVER_AVAILABILITY, context, policy, now).contains("OPTIMIZATION_INPUT_STALE"));
    }
    @Test void policyRequiresAuthoredAllowlistsAndRegistrations() {
        assertThrows(com.company.logicstic.exception.ApiException.class, () -> new Policy("OPT_ELIGIBILITY_V1", 1, 72, "OPT_SOURCE_V1", 1, Map.of(), policy.qualifiedSources()));
        assertThrows(com.company.logicstic.exception.ApiException.class, () -> new Policy("OPT_ELIGIBILITY_V1", 1, 72, "OPT_SOURCE_V1", 1, policy.statusAllowlists(), Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> policy.statusAllowlists().get(EntityKind.LOAD).add("ACTIVE"));
    }
    @Test void horizonIsExactlySeventyTwoHoursFromRunInstant() {
        CandidateContext wrong = new CandidateContext(context.loadId(), context.tripId(), context.driverId(), context.truckId(), now, now.plusSeconds(3600));
        assertEquals(List.of("INVALID_OPTIMIZATION_CONTEXT"), feasibility.evaluate(wrong, facts(), bundle(), hos(), policy, now).rejectionCodes());
    }
}
