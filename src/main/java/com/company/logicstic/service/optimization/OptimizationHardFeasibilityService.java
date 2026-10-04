package com.company.logicstic.service.optimization;

import com.company.logicstic.integration.hos.HosFeasibilityService.Assessment;
import com.company.logicstic.service.optimization.domain.OptimizationEvidence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OptimizationHardFeasibilityService {
    private final OptimizationEvidenceValidator validator;
    public OptimizationHardFeasibilityService(OptimizationEvidenceValidator validator) { this.validator = validator; }
    public record Result(boolean feasible, List<String> rejectionCodes) {
        public Result { rejectionCodes = List.copyOf(rejectionCodes); }
    }

    public Result evaluate(CandidateContext context, Facts facts, Bundle bundle, Input<Assessment> hos,
                           Policy policy, Instant evaluatedAt) {
        var reasons = new ArrayList<String>();
        if (context == null || policy == null || evaluatedAt == null || context.loadId() == null || context.tripId() == null
                || context.driverId() == null || context.truckId() == null || context.planningStart() == null
                || context.planningEnd() == null || !context.planningEnd().equals(context.planningStart().plus(Duration.ofHours(72)))) {
            return new Result(false, List.of("INVALID_OPTIMIZATION_CONTEXT"));
        }
        if (facts == null) reasons.add("OPTIMIZATION_CONTEXT_UNAVAILABLE");
        else {
            for (EntityKind kind : EntityKind.values()) {
                String status = facts.statuses().get(kind);
                if (status == null || !policy.statusAllowlists().get(kind).contains(status)) reasons.add("OPTIMIZATION_STATUS_NOT_ALLOWED");
            }
            if (!facts.sameTenantAndTripLoadContext()) reasons.add("OPTIMIZATION_CONTEXT_MISMATCH");
            if (!facts.loadPreDispatch() || !facts.tripPreDispatch()) reasons.add("OPTIMIZATION_STATUS_NOT_ALLOWED");
            if (facts.requestedPickupBusinessDate() == null) reasons.add("RATING_PRICING_DATE_REQUIRED");
            if (facts.acceptedRatingSnapshotId() == null) reasons.add("OPTIMIZATION_ACCEPTED_RATING_REQUIRED");
            if (!facts.routingLocationsAvailable()) reasons.add("OPTIMIZATION_ROUTING_CONTEXT_REQUIRED");
            if (facts.conflictingActiveAssignment()) reasons.add("OPTIMIZATION_ASSIGNMENT_CONFLICT");
        }
        if (bundle == null) return new Result(false, append(reasons, "OPTIMIZATION_INPUT_EVIDENCE_REQUIRED"));
        check(bundle.location(), Kind.VEHICLE_LOCATION, context, policy, evaluatedAt, reasons);
        check(bundle.driverAvailability(), Kind.DRIVER_AVAILABILITY, context, policy, evaluatedAt, reasons);
        check(bundle.truckAvailability(), Kind.TRUCK_AVAILABILITY, context, policy, evaluatedAt, reasons);
        check(bundle.capacity(), Kind.CAPACITY, context, policy, evaluatedAt, reasons);
        check(bundle.qualification(), Kind.QUALIFICATION, context, policy, evaluatedAt, reasons);
        check(bundle.route(), Kind.ROUTE, context, policy, evaluatedAt, reasons);
        check(hos, Kind.HOS, context, policy, evaluatedAt, reasons);
        if (value(bundle.location()) != null) {
            Location l = bundle.location().value();
            if (!"WGS84".equals(bundle.location().provenance() == null ? null : bundle.location().provenance().unit())
                    || l.latitude() == null || l.longitude() == null || l.latitude().abs().compareTo(java.math.BigDecimal.valueOf(90)) > 0
                    || l.longitude().abs().compareTo(java.math.BigDecimal.valueOf(180)) > 0) reasons.add("VEHICLE_LOCATION_INVALID");
        }
        availability(bundle.driverAvailability(), context, "DRIVER_UNAVAILABLE", reasons);
        availability(bundle.truckAvailability(), context, "TRUCK_UNAVAILABLE", reasons);
        if (value(bundle.capacity()) != null) {
            Capacity c = bundle.capacity().value();
            if (!unit(bundle.capacity(), "POUND") || !validator.valid(c.cargo()) || !validator.valid(c.truck())) reasons.add("CAPACITY_EVIDENCE_UNAVAILABLE");
            else if (c.cargo().normalizedPounds().compareTo(c.truck().normalizedPounds()) > 0) reasons.add("CAPACITY_INFEASIBLE");
        }
        if (value(bundle.qualification()) != null) {
            Qualification q = bundle.qualification().value();
            if (!unit(bundle.qualification(), "QUALIFICATION") || q.effectiveFrom() == null || q.effectiveUntil() == null
                    || q.effectiveFrom().isAfter(context.planningStart()) || q.effectiveUntil().isBefore(context.planningEnd())) reasons.add("QUALIFICATION_EVIDENCE_UNAVAILABLE");
            if (!q.qualifiedDriver() || !q.validLicense()) reasons.add("DRIVER_QUALIFICATION_INFEASIBLE");
            if (!q.equipmentMatches()) reasons.add("EQUIPMENT_INFEASIBLE");
            if (q.hazmatRequired() && (!q.hazmatDriver() || !q.hazmatTruck())) reasons.add("HAZMAT_INFEASIBLE");
            if (!q.operational()) reasons.add("TRUCK_UNAVAILABLE");
            if (!q.maintenanceClear()) reasons.add("MAINTENANCE_BLOCK");
        }
        if (value(bundle.route()) != null) {
            Route r = bundle.route().value();
            if (!unit(bundle.route(), "MILE") || !validator.valid(r.deadhead()) || !validator.valid(r.loadAttributedLoadedMiles())
                    || blank(r.simulatedRoutePlanReference()) || blank(r.simulatedRoutePlanVersion())) reasons.add("OPTIMIZATION_ROUTE_EVIDENCE_INVALID");
            if (!r.pickupReachable()) reasons.add("PICKUP_UNREACHABLE");
            if (r.appointmentStart() == null || r.predictedArrivalAtPickup() == null) reasons.add("ON_TIME_UTILITY_UNAVAILABLE");
        }
        if (value(hos) != null) {
            Assessment h = hos.value();
            Route r = value(bundle.route());
            if (!unit(hos, "RATIO") || blank(h.ruleSetCode()) || blank(h.ruleSetVersion()) || r == null
                    || blank(h.simulatedRoutePlanReference()) || blank(h.simulatedRoutePlanVersion())
                    || !java.util.Objects.equals(r.simulatedRoutePlanReference(), h.simulatedRoutePlanReference())
                    || !java.util.Objects.equals(r.simulatedRoutePlanVersion(), h.simulatedRoutePlanVersion())) reasons.add("HOS_EVIDENCE_CONTEXT_MISMATCH");
            if (!h.driveFeasible() || !h.dutyFeasible() || !h.breakFeasible() || !h.cycleFeasible()
                    || !h.serviceFeasible() || !h.nextAvailableFeasible()) reasons.add("HOS_INFEASIBLE");
            if (h.minimumHosHeadroomRatio() == null) reasons.add("HOS_UTILITY_EVIDENCE_REQUIRED");
        }
        List<String> distinct = reasons.stream().distinct().toList();
        return new Result(distinct.isEmpty(), distinct);
    }
    private void check(Input<?> input, Kind kind, CandidateContext context, Policy policy, Instant at, List<String> reasons) {
        reasons.addAll(validator.validate(input, kind, context, policy, at));
    }
    private static void availability(Input<Availability> input, CandidateContext context, String code, List<String> reasons) {
        Availability a = value(input);
        if (a != null && (!unit(input, "INTERVAL") || !a.available() || a.coversFrom() == null || a.coversUntil() == null
                || a.coversFrom().isAfter(context.planningStart()) || a.coversUntil().isBefore(context.planningEnd()))) reasons.add(code);
    }
    private static <T> T value(Input<T> input) { return input == null ? null : input.value(); }
    private static boolean unit(Input<?> input, String expected) { return input.provenance() != null && expected.equals(input.provenance().unit()); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static List<String> append(List<String> list, String code) { list.add(code); return list.stream().distinct().toList(); }
}
