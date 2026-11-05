package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.dto.driverprofile.CreateDriverEmergencyContactRequest;
import com.company.logicstic.entity.DriverEmergencyContact;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.mapper.DriverEmergencyContactMapper;
import com.company.logicstic.repository.DriverEmergencyContactRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverEmergencyContactServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverEmergencyContactRepository contacts;
    private DriverEmergencyContactService service;
    private DriverEmergencyContact existingPrimary;

    @BeforeEach
    void setUp() {
        contacts = mock(DriverEmergencyContactRepository.class);
        var employees = mock(EmployeeRepository.class);
        service = new DriverEmergencyContactService(contacts, employees, new DriverEmergencyContactMapper(),
                mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        existingPrimary = new DriverEmergencyContact();
        existingPrimary.setId(UUID.randomUUID());
        existingPrimary.setEmployee(driver);
        existingPrimary.setPrimaryContact(true);
        existingPrimary.setActive(true);
        when(contacts.findFirstByEmployee_IdAndPrimaryContactTrueAndActiveTrue(driverId))
                .thenReturn(Optional.of(existingPrimary));
        when(contacts.saveAndFlush(any(DriverEmergencyContact.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void promotingNewContactReleasesTheExistingPrimaryFirst() {
        var view = service.create(driverId,
                new CreateDriverEmergencyContactRequest("Nguyễn An", "Parent", "+84901234567", null, true));

        assertTrue(view.primary());
        assertFalse(existingPrimary.isPrimaryContact());
        var saved = org.mockito.ArgumentCaptor.forClass(DriverEmergencyContact.class);
        verify(contacts, times(2)).saveAndFlush(saved.capture());
        assertSame(existingPrimary, saved.getAllValues().getFirst());
        assertTrue(saved.getAllValues().getLast().isPrimaryContact());
    }

    @Test
    void rejectsUnsupportedSortBeforeQueryingContactList() {
        assertThrows(com.company.logicstic.exception.ApiException.class,
                () -> service.list(driverId, 1, 20, "phoneNumber", false));
        verify(contacts, never()).findByEmployee_IdAndActiveTrue(any(), any());
    }
}
