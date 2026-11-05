package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.repository.DriverBankAccountRepository;
import com.company.logicstic.repository.DriverCertificationRepository;
import com.company.logicstic.repository.DriverDependentRepository;
import com.company.logicstic.repository.DriverDirectoryRepository;
import com.company.logicstic.repository.DriverHealthCheckRepository;
import com.company.logicstic.repository.DriverInsuranceRepository;
import com.company.logicstic.repository.DriverLicenseRepository;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.repository.EmploymentContractRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class DriverServiceSearchTest {
    private DriverDirectoryRepository directory;
    private EmployeeService employeeService;
    private DriverService service;

    @BeforeEach
    void setUp() {
        directory = mock(DriverDirectoryRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new DriverService(employeeService, mock(EmployeeRepository.class),
                mock(DriverLicenseRepository.class), mock(DriverHealthCheckRepository.class),
                mock(DriverCertificationRepository.class), mock(EmploymentContractRepository.class),
                mock(DriverInsuranceRepository.class), mock(DriverDependentRepository.class),
                mock(DriverBankAccountRepository.class), directory);
        when(directory.searchByAvailability(any(), any(), any(), any(), any())).thenReturn(Page.empty());
    }

    @Test
    void availabilityIsNormalizedAndUsesBoundedWhitelistedPagedQuery() {
        service.search(null, null, 1, 500, "lastName", false, " available ");

        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(directory).searchByAvailability(eq(EmployeeType.DRIVER), eq(""), isNull(), eq("AVAILABLE"), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());
        assertTrue(pageable.getValue().getSort().getOrderFor("id") != null);
    }

    @Test
    void rejectsUnknownAvailabilityWithoutQueryingDirectory() {
        assertThrows(ApiException.class, () -> service.search(null, null, 1, 20, "lastName", false, "DRIVING"));
        verify(directory, never()).searchByAvailability(any(), any(), any(), any(), any());
    }

    @Test
    void normalSearchDelegatesToEmployeeTypeDriverSearch() {
        service.search(null, null, 1, 20, "lastName", false, null);
        verify(employeeService).searchDrivers(isNull(), isNull(), eq(1), eq(20), eq("lastName"), eq(false));
    }

    @Test
    void createUsesDriverSpecificEmployeePersistence() {
        service.create(null);
        verify(employeeService).createDriver(null);
    }
}
