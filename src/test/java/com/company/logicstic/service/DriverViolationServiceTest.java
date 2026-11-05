package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.driverprofile.CreateDriverViolationRequest;
import com.company.logicstic.entity.DriverViolation;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.entity.Trip;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverViolationMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.DriverViolationRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TripDriverAssignmentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class DriverViolationServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverViolationRepository violations;
    private EmployeeRepository employees;
    private TripDriverAssignmentRepository assignments;
    private DriverViolationService service;

    @BeforeEach
    void setUp() {
        violations = mock(DriverViolationRepository.class);
        employees = mock(EmployeeRepository.class);
        assignments = mock(TripDriverAssignmentRepository.class);
        service = new DriverViolationService(violations, employees, mock(DocumentRepository.class),
                assignments, new DriverViolationMapper());
        Employee driver = driver();
        when(employees.findById(driverId)).thenReturn(Optional.of(driver));
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(violations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void normalizesAndCreatesViolationWithVietnameseDongDefault() {
        var view = service.create(driverId, new CreateDriverViolationRequest(
                OffsetDateTime.parse("2026-10-01T09:00:00Z"), " speed ", " Exceeded speed limit ",
                new BigDecimal("500000.00"), null, null, null, null));

        assertEquals("speed", view.violationType());
        assertEquals("Exceeded speed limit", view.description());
        assertEquals("VND", view.fineCurrency());
        assertEquals("OPEN", view.resolutionStatus());
        assertTrue(view.active());
    }

    @Test
    void rejectsTripThatWasNotAssignedAtViolationTime() {
        UUID tripId = UUID.randomUUID();
        OffsetDateTime date = OffsetDateTime.parse("2026-10-01T09:00:00Z");
        when(assignments.findActiveAssignmentsForDriver(driverId, date)).thenReturn(List.of());

        assertThrows(ApiException.class, () -> service.create(driverId, new CreateDriverViolationRequest(
                date, "SPEED", "Exceeded speed limit", null, null, tripId, null, null)));
        verify(violations, never()).saveAndFlush(any());
    }

    @Test
    void listUsesBoundedPageAndDeactivationKeepsTheRecord() {
        when(violations.findByEmployee_IdAndActiveTrue(eq(driverId), any())).thenReturn(Page.empty());
        service.list(driverId, 1, 500, "violationDate", true);
        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(violations).findByEmployee_IdAndActiveTrue(eq(driverId), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());

        DriverViolation record = new DriverViolation();
        record.setId(UUID.randomUUID());
        record.setEmployee(driver());
        record.setActive(true);
        when(violations.findByIdAndEmployee_Id(record.getId(), driverId)).thenReturn(Optional.of(record));
        service.deactivate(driverId, record.getId());
        assertFalse(record.isActive());
        verify(violations).saveAndFlush(record);
    }

    @Test
    void rejectsUnsupportedSortInsteadOfPassingItToJpa() {
        assertThrows(ApiException.class, () -> service.list(driverId, 1, 20, "description;drop", false));
        verify(violations, never()).findByEmployee_IdAndActiveTrue(any(), any());
    }

    private Employee driver() {
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        return driver;
    }
}
