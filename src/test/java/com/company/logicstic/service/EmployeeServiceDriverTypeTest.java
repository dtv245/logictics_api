package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.employee.CreateEmployeeRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.TenantRoleRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmployeeServiceDriverTypeTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final TenantRoleRepository roles = mock(TenantRoleRepository.class);
    private final EmployeeService service = new EmployeeService(employees, roles);

    @Test
    void createDriverSetsDriverTypeAndNeverPersistsRoleId() {
        UUID suppliedRoleId = UUID.randomUUID();
        when(employees.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(UUID.randomUUID());
            return employee;
        });

        service.createDriver(request(suppliedRoleId));

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees).save(saved.capture());
        assertEquals(EmployeeType.DRIVER, saved.getValue().getEmployeeType());
        assertNull(saved.getValue().getRole());
        verifyNoInteractions(roles);
    }

    @Test
    void genericEmployeeCreationRejectsDriverRole() {
        UUID driverRoleId = UUID.randomUUID();
        TenantRole driverRole = new TenantRole();
        driverRole.setId(driverRoleId);
        driverRole.setNormalizedName("DRIVER");
        when(roles.findById(driverRoleId)).thenReturn(Optional.of(driverRole));

        assertThrows(BadRequestException.class, () -> service.create(request(driverRoleId)));
        verify(employees, never()).save(any(Employee.class));
    }

    private static CreateEmployeeRequest request(UUID roleId) {
        return new CreateEmployeeRequest("driver-type@example.test", "Test", "Driver", null,
                "HOURLY", "ACTIVE", OffsetDateTime.parse("2026-01-01T00:00:00Z"), roleId,
                BigDecimal.ZERO, "VND", null, null, null, null, null, null);
    }
}
