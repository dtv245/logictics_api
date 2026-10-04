package com.company.logicstic.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import com.company.logicstic.dto.trip.AssignDriverRequest;
import com.company.logicstic.dto.trip.TripDriverAssignmentView;
import com.company.logicstic.dto.trip.TripStopView;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.entity.TripDriverAssignment;
import com.company.logicstic.entity.TripStop;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import com.company.logicstic.repository.TripRepository;
import com.company.logicstic.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripExecutionService {

    private static final Set<String> ASSIGNMENT_TYPES = Set.of("PRIMARY", "SECONDARY", "TEAM", "RELIEF");

    private final TripRepository tripRepository;
    private final EmployeeRepository employeeRepository;
    private final TripDriverAssignmentRepository assignmentRepository;
    private final TripStopRepository tripStopRepository;

    @Transactional
    public TripDriverAssignmentView assignDriver(UUID tripId, AssignDriverRequest req) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));

        Employee driver = employeeRepository.findById(req.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + req.driverId()));

        OffsetDateTime effectiveFrom = req.effectiveFrom() != null ? req.effectiveFrom() : OffsetDateTime.now();
        String assignmentType = req.assignmentType() != null && !req.assignmentType().isBlank()
                ? req.assignmentType().toUpperCase() : "PRIMARY";
        if (!ASSIGNMENT_TYPES.contains(assignmentType)) {
            throw new BadRequestException("Unsupported trip driver assignment type: " + assignmentType);
        }

        TripDriverAssignment assignment = new TripDriverAssignment();
        assignment.setTrip(trip);
        assignment.setDriver(driver);
        assignment.setAssignmentType(assignmentType);
        assignment.setAssignedAt(OffsetDateTime.now());
        assignment.setEffectiveFrom(effectiveFrom);
        assignment.setPlannedMiles(req.plannedMiles());

        TripDriverAssignment saved = assignmentRepository.save(assignment);
        return toAssignmentView(saved);
    }

    @Transactional
    public TripDriverAssignmentView unassignDriver(UUID tripId, UUID assignmentId) {
        TripDriverAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        if (!assignment.getTrip().getId().equals(tripId)) {
            throw new BadRequestException("Assignment " + assignmentId + " does not belong to Trip " + tripId);
        }
        if (assignment.getEffectiveTo() != null) {
            throw new BadRequestException("Assignment " + assignmentId + " is already closed");
        }

        // Soft close: set effectiveTo, DO NOT delete historical record!
        assignment.setEffectiveTo(OffsetDateTime.now());
        TripDriverAssignment saved = assignmentRepository.save(assignment);
        return toAssignmentView(saved);
    }

    @Transactional(readOnly = true)
    public List<TripDriverAssignmentView> getTripDrivers(UUID tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException("Trip not found: " + tripId);
        }
        return assignmentRepository.findByTripIdOrderByEffectiveFromDesc(tripId).stream()
                .map(this::toAssignmentView)
                .toList();
    }

    @Transactional
    public TripStopView arriveStop(UUID stopId) {
        TripStop stop = findStop(stopId);
        requireStatus(stop, Set.of("PENDING", "EN_ROUTE"), "ARRIVED");
        stop.setArrivedAt(OffsetDateTime.now());
        stop.setStatus("ARRIVED");
        return toStopView(tripStopRepository.save(stop));
    }

    @Transactional
    public TripStopView startStopService(UUID stopId) {
        TripStop stop = findStop(stopId);
        requireStatus(stop, Set.of("ARRIVED"), "SERVICE_STARTED");
        stop.setServiceStartedAt(OffsetDateTime.now());
        stop.setStatus("SERVICE_STARTED");
        return toStopView(tripStopRepository.save(stop));
    }

    @Transactional
    public TripStopView completeStopService(UUID stopId) {
        TripStop stop = findStop(stopId);
        requireStatus(stop, Set.of("SERVICE_STARTED"), "SERVICE_COMPLETED");
        stop.setServiceCompletedAt(OffsetDateTime.now());
        stop.setStatus("SERVICE_COMPLETED");
        return toStopView(tripStopRepository.save(stop));
    }

    @Transactional
    public TripStopView departStop(UUID stopId) {
        TripStop stop = findStop(stopId);
        requireStatus(stop, Set.of("SERVICE_COMPLETED"), "DEPARTED");
        stop.setDepartedAt(OffsetDateTime.now());
        stop.setStatus("DEPARTED");
        return toStopView(tripStopRepository.save(stop));
    }

    @Transactional(readOnly = true)
    public List<TripStopView> getTripStops(UUID tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException("Trip not found: " + tripId);
        }
        return tripStopRepository.findByTripIdOrderByOrderAsc(tripId).stream()
                .map(this::toStopView)
                .toList();
    }

    private TripStop findStop(UUID stopId) {
        return tripStopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("TripStop not found: " + stopId));
    }

    private void requireStatus(TripStop stop, Set<String> allowedCurrentStatuses, String targetStatus) {
        String currentStatus = stop.getStatus() == null ? "PENDING" : stop.getStatus();
        if (!allowedCurrentStatuses.contains(currentStatus)) {
            throw new BadRequestException("Trip stop cannot transition from " + currentStatus + " to " + targetStatus);
        }
    }

    private TripDriverAssignmentView toAssignmentView(TripDriverAssignment a) {
        String driverName = a.getDriver() != null
                ? (a.getDriver().getFirstName() + " " + a.getDriver().getLastName()).trim()
                : "Unknown";
        boolean isActive = a.getEffectiveTo() == null || a.getEffectiveTo().isAfter(OffsetDateTime.now());

        return new TripDriverAssignmentView(
                a.getId(),
                a.getTrip().getId(),
                a.getDriver().getId(),
                driverName,
                a.getAssignmentType(),
                a.getAssignedAt(),
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.getPlannedMiles(),
                a.getActualMiles(),
                isActive
        );
    }

    private TripStopView toStopView(TripStop s) {
        Long dwell = null;
        if (s.getArrivedAt() != null && s.getDepartedAt() != null) {
            dwell = Duration.between(s.getArrivedAt(), s.getDepartedAt()).toMinutes();
        }

        return new TripStopView(
                s.getId(),
                s.getTrip() != null ? s.getTrip().getId() : null,
                s.getLoad() != null ? s.getLoad().getId() : null,
                s.getOrder(),
                s.getType(),
                s.getStatus(),
                s.getAppointmentStart(),
                s.getAppointmentEnd(),
                s.getArrivedAt(),
                s.getServiceStartedAt(),
                s.getServiceCompletedAt(),
                s.getDepartedAt(),
                dwell,
                s.getAddressCity(),
                s.getAddressState(),
                s.getLocationLatitude(),
                s.getLocationLongitude()
        );
    }
}
