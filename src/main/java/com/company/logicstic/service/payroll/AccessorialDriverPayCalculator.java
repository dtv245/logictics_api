package com.company.logicstic.service.payroll;

import com.company.logicstic.common.CurrencyGuard;
import com.company.logicstic.common.FinancialRoundingPolicy;
import com.company.logicstic.entity.*;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import com.company.logicstic.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class AccessorialDriverPayCalculator {
    private final TripDriverAssignmentRepository assignments;
    private final TripStopRepository stops;
    private final FinancialRoundingPolicy rounding;
    public record Result(UUID chargeId, Long chargeVersion, UUID driverId, UUID tripId, List<UUID> assignmentIds,
            OffsetDateTime occurredAt, OffsetDateTime approvedAt, BigDecimal driverPayAmount, BigDecimal amount, String currency) {}

    public Optional<Result> calculate(AccessorialCharge charge, UUID driverId, LocalDate from, LocalDate through, String currency) {
        if (!"APPROVED".equalsIgnoreCase(charge.getStatus())) return Optional.empty();
        BigDecimal amount = charge.getDriverPayAmount();
        if (amount == null || amount.signum() < 0) throw invalid("Explicit non-negative driver_pay_amount required");
        if (amount.signum() == 0) return Optional.empty();
        OffsetDateTime at = charge.getOccurredAt();
        if (at == null) throw invalid("Positive driver pay requires occurred_at for historical attribution");
        LocalDate date = at.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        if (date.isBefore(from) || date.isAfter(through)) return Optional.empty();
        Map<UUID,Trip> trips = new LinkedHashMap<>();
        if (charge.getTrip() != null) trips.put(charge.getTrip().getId(),charge.getTrip());
        else if (charge.getTripStop() != null && charge.getTripStop().getTrip() != null)
            trips.put(charge.getTripStop().getTrip().getId(),charge.getTripStop().getTrip());
        else if (charge.getLoad() != null) for (var stop : stops.findByLoadId(charge.getLoad().getId()))
            if (stop.getTrip() != null) trips.put(stop.getTrip().getId(),stop.getTrip());
        List<TripDriverAssignment> active = trips.keySet().stream().flatMap(id -> assignments.findByTripIdOrderByEffectiveFromDesc(id).stream())
                .filter(a -> a.getDriver() != null && a.getEffectiveFrom() != null && !a.getEffectiveFrom().isAfter(at)
                        && (a.getEffectiveTo() == null || a.getEffectiveTo().isAfter(at))).toList();
        Set<UUID> drivers = new HashSet<>(); active.forEach(a -> drivers.add(a.getDriver().getId()));
        if (drivers.size() != 1) throw invalid("No unique historical driver recipient at charge occurrence");
        if (!drivers.contains(driverId)) return Optional.empty();
        CurrencyGuard.requireSameCurrency(currency,charge.getCurrency());
        Set<UUID> activeTrips = new HashSet<>(); active.forEach(a -> activeTrips.add(a.getTrip().getId()));
        UUID tripId = activeTrips.size() == 1 ? activeTrips.iterator().next() : null;
        return Optional.of(new Result(charge.getId(),charge.getVersion(),driverId,tripId,active.stream().map(TripDriverAssignment::getId).sorted().toList(),
                at,charge.getApprovedAt(),amount,rounding.money(amount,currency,FinancialRoundingPolicy.Boundary.ALLOCATION),CurrencyGuard.canonical(currency)));
    }
    private BadRequestException invalid(String message) { return new BadRequestException("ACCESSORIAL_PAY_VALIDATION_REQUIRED",message); }
}
