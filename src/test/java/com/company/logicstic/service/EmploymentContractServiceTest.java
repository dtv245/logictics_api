package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.driverprofile.CreateEmploymentContractRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.EmploymentContractMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.EmploymentContractRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmploymentContractServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private EmploymentContractRepository contracts;
    private EmploymentContractService service;

    @BeforeEach
    void setUp() {
        contracts = mock(EmploymentContractRepository.class);
        var employees = mock(EmployeeRepository.class);
        service = new EmploymentContractService(contracts, employees, mock(DocumentRepository.class),
                new EmploymentContractMapper(), mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(contracts.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsNormalizedFixedTermContract() {
        var view = service.create(driverId, new CreateEmploymentContractRequest(" hd-2026-01 ", "FIXED_TERM",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2027, 1, 1),
                null, null, null));
        assertEquals("HD-2026-01", view.contractNumber());
        assertEquals("FIXED_TERM", view.contractType());
        assertTrue(view.active());
    }

    @Test
    void rejectsInvalidContractDateRulesBeforePersistence() {
        assertThrows(ApiException.class, () -> service.create(driverId,
                new CreateEmploymentContractRequest("HD-1", "FIXED_TERM", LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), null, null, null)));
        assertThrows(ApiException.class, () -> service.create(driverId,
                new CreateEmploymentContractRequest("HD-2", "INDEFINITE", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), null, null, null)));
        assertThrows(ApiException.class, () -> service.create(driverId,
                new CreateEmploymentContractRequest("HD-3", "FIXED_TERM", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1), null, null, null, null)));
        verify(contracts, never()).saveAndFlush(any());
    }
}
