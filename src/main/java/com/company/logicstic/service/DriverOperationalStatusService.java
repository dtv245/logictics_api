package com.company.logicstic.service;

import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.driverprofile.DriverOperationalStatusView;
import com.company.logicstic.dto.driverprofile.UpdateDriverOperationalStatusRequest;
import com.company.logicstic.entity.DriverOperationalStatus;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverOperationalStatusMapper;
import com.company.logicstic.repository.DriverOperationalStatusRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TruckRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DriverOperationalStatusService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final Set<String> AVAILABILITIES = Set.of("AVAILABLE", "ON_TRIP", "OFF_DUTY", "ON_LEAVE", "SUSPENDED");

    private final DriverOperationalStatusRepository statuses;
    private final EmployeeRepository employees;
    private final TruckRepository trucks;
    private final DriverOperationalStatusMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverOperationalStatusService(DriverOperationalStatusRepository statuses, EmployeeRepository employees,
            TruckRepository trucks, DriverOperationalStatusMapper mapper, CurrentUserService currentUserService) {
        this.statuses = statuses;
        this.employees = employees;
        this.trucks = trucks;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public DriverOperationalStatusView get(UUID driverId) {
        requireDriver(driverId);
        DriverOperationalStatus status = statuses.findByEmployee_Id(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver operational status not found: " + driverId));
        return toView(status, driverId);
    }

    public DriverOperationalStatusView getMine() {
        return get(requireDriverIdentity().employeeId());
    }

    @Transactional
    public DriverOperationalStatusView update(UUID driverId, UpdateDriverOperationalStatusRequest request) {
        Employee driver = employees.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + driverId));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + driverId);
        }

        if (request == null || request.availability() == null || request.availability().isBlank()) {
            throw new BadRequestException("INVALID_DRIVER_AVAILABILITY", "A driver availability is required");
        }
        String availability = request.availability().trim().toUpperCase(Locale.ROOT);
        if (!AVAILABILITIES.contains(availability)) {
            throw new BadRequestException("INVALID_DRIVER_AVAILABILITY", "Choose a supported driver availability");
        }
        boolean hasLatitude = request.lastKnownLatitude() != null;
        boolean hasLongitude = request.lastKnownLongitude() != null;
        boolean hasTimestamp = request.lastKnownAt() != null;
        if (hasLatitude != hasLongitude || hasLatitude != hasTimestamp) {
            throw new BadRequestException("INVALID_DRIVER_LOCATION", "Latitude, longitude, and capture time must be supplied together");
        }
        if (request.leaveBalanceDays() != null && request.leaveBalanceDays().signum() < 0) {
            throw new BadRequestException("INVALID_LEAVE_BALANCE", "Leave balance cannot be negative");
        }
        if (request.defaultVehicleId() != null && !trucks.existsById(request.defaultVehicleId())) {
            throw new ResourceNotFoundException("Default vehicle not found: " + request.defaultVehicleId());
        }

        DriverOperationalStatus status = statuses.findByEmployee_Id(driverId).orElseGet(DriverOperationalStatus::new);
        status.setEmployee(driver);
        status.setAvailability(availability);
        status.setDefaultVehicleId(request.defaultVehicleId());
        status.setLastKnownLatitude(request.lastKnownLatitude());
        status.setLastKnownLongitude(request.lastKnownLongitude());
        status.setLastKnownAt(request.lastKnownAt());
        status.setLeaveBalanceDays(request.leaveBalanceDays());
        DriverOperationalStatus saved = statuses.saveAndFlush(status);
        return toView(saved, driverId);
    }

    private DriverOperationalStatusView toView(DriverOperationalStatus status, UUID driverId) {
        var currentTrips = statuses.findCurrentTripIds(driverId, OffsetDateTime.now(ZoneOffset.UTC));
        UUID currentTripId = currentTrips.size() == 1 ? currentTrips.getFirst() : null;
        return mapper.toView(status, currentTripId);
    }

    private Employee requireDriver(UUID id) {
        Employee driver = employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private CurrentUserResponse requireDriverIdentity() {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) {
            throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        }
        return identity;
    }
}
