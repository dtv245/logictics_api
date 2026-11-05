package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.driverprofile.CreateDriverAccidentRequest;
import com.company.logicstic.entity.AccidentReport;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.entity.Truck;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverAccidentMapper;
import com.company.logicstic.repository.AccidentReportProfileRepository;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import com.company.logicstic.repository.TruckRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverAccidentServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private final UUID truckId = UUID.randomUUID();
    private AccidentReportProfileRepository accidents;
    private EmployeeRepository employees;
    private TruckRepository trucks;
    private DriverAccidentService service;

    @BeforeEach
    void setUp() {
        accidents = mock(AccidentReportProfileRepository.class);
        employees = mock(EmployeeRepository.class);
        trucks = mock(TruckRepository.class);
        service = new DriverAccidentService(accidents, employees, trucks, mock(DocumentRepository.class),
                mock(TripDriverAssignmentRepository.class), new DriverAccidentMapper());
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        Truck truck = new Truck();
        truck.setId(truckId);
        when(trucks.findById(truckId)).thenReturn(Optional.of(truck));
        when(accidents.saveAndFlush(any())).thenAnswer(call -> {
            AccidentReport report = call.getArgument(0);
            if (report.getId() == null) report.setId(UUID.randomUUID());
            return report;
        });
        when(accidents.findDocumentIdsByAccidentReportIds(any())).thenReturn(List.of());
    }

    @Test
    void rejectsInconsistentInjuryAndDamageDetails() {
        var request = request(false, 1, false, new BigDecimal("100.00"));
        assertThrows(ApiException.class, () -> service.create(driverId, request));
        verify(trucks, never()).findById(any());
        verify(accidents, never()).saveAndFlush(any());
    }

    @Test
    void createsReportInExistingAccidentAggregateWithDefaultStatus() {
        var view = service.create(driverId, request(false, null, false, null));
        assertNotNull(view.id());
        assertEquals(driverId, view.driverId());
        assertEquals(truckId, view.truckId());
        assertEquals("COLLISION", view.accidentType());
        assertEquals("REPORTED", view.status());
        assertTrue(view.documentIds().isEmpty());
    }

    private CreateDriverAccidentRequest request(boolean injuries, Integer count, boolean damaged, BigDecimal cost) {
        return new CreateDriverAccidentRequest(truckId, null, "collision", "minor",
                OffsetDateTime.parse("2026-10-01T09:00:00Z"), 10.762622, 106.660172,
                "Ho Chi Minh City", "Road collision", null, null, injuries, count, null,
                damaged, null, cost, true, false, null, false, null, null, null, null);
    }
}
