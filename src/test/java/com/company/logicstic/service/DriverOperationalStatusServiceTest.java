package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.driverprofile.UpdateDriverOperationalStatusRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverOperationalStatusMapper;
import com.company.logicstic.repository.DriverOperationalStatusRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TruckRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverOperationalStatusServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverOperationalStatusRepository statuses;
    private EmployeeRepository employees;
    private TruckRepository trucks;
    private DriverOperationalStatusService service;

    @BeforeEach
    void setUp() {
        statuses = mock(DriverOperationalStatusRepository.class);
        employees = mock(EmployeeRepository.class);
        trucks = mock(TruckRepository.class);
        service = new DriverOperationalStatusService(statuses, employees, trucks,
                new DriverOperationalStatusMapper(), mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(statuses.findByEmployee_Id(driverId)).thenReturn(Optional.empty());
        when(statuses.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void updatesAvailabilityAndDerivesCurrentTripFromSingleActiveAssignment() {
        UUID tripId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        when(trucks.existsById(vehicleId)).thenReturn(true);
        when(statuses.findCurrentTripIds(eq(driverId), any(OffsetDateTime.class))).thenReturn(List.of(tripId));

        var view = service.update(driverId, request("ON_TRIP", vehicleId, "10.7626220", "106.6601720",
                OffsetDateTime.parse("2026-10-29T07:00:00Z"), "5.50"));

        assertEquals("ON_TRIP", view.availability());
        assertEquals(vehicleId, view.defaultVehicleId());
        assertEquals(tripId, view.currentTripId());
        assertEquals(new BigDecimal("10.7626220"), view.lastKnownLatitude());
        assertEquals(new BigDecimal("5.50"), view.leaveBalanceDays());
        verify(statuses).saveAndFlush(any());
    }

    @Test
    void rejectsPartialLocationAndDoesNotGuessWhenAssignmentsOverlap() {
        assertThrows(ApiException.class, () -> service.update(driverId,
                request("AVAILABLE", null, "10.0", null, null, null)));
        verify(statuses, never()).saveAndFlush(any());

        when(statuses.findCurrentTripIds(eq(driverId), any(OffsetDateTime.class)))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        var view = service.update(driverId, request("AVAILABLE", null, null, null, null, null));
        assertNull(view.currentTripId());
    }

    private static UpdateDriverOperationalStatusRequest request(String availability, UUID vehicleId,
            String latitude, String longitude, OffsetDateTime lastKnownAt, String leaveBalance) {
        return new UpdateDriverOperationalStatusRequest(availability, vehicleId,
                latitude == null ? null : new BigDecimal(latitude), longitude == null ? null : new BigDecimal(longitude),
                lastKnownAt, leaveBalance == null ? null : new BigDecimal(leaveBalance));
    }
}
