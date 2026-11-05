package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.dto.driverprofile.CreateDriverDependentRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverDependentMapper;
import com.company.logicstic.repository.DriverDependentRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverDependentServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverDependentRepository dependents;
    private DriverDependentService service;
    private DriverPiiCipher cipher;

    @BeforeEach
    void setUp() {
        dependents = mock(DriverDependentRepository.class);
        var employees = mock(EmployeeRepository.class);
        var properties = new DriverPiiProperties();
        properties.setActiveKeyId("test");
        properties.setEncryptionKeys("test:" + Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        properties.setIndexKey(Base64.getEncoder().encodeToString(
                "index-key-0123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        cipher = new DriverPiiCipher(properties);
        service = new DriverDependentService(dependents, employees, cipher, new DriverDependentMapper(cipher),
                mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(dependents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void encryptsDependentIdentifiersAndReturnsMaskedView() {
        var view = service.create(driverId, new CreateDriverDependentRequest("Nguyễn An", "Child",
                LocalDate.of(2015, 1, 2), "079123456789", "1234567890",
                LocalDate.of(2025, 1, 1), null));
        assertEquals("****6789", view.nationalIdNumber());
        assertEquals("****7890", view.taxCode());
        var stored = org.mockito.ArgumentCaptor.forClass(com.company.logicstic.entity.DriverDependent.class);
        verify(dependents).saveAndFlush(stored.capture());
        assertTrue(stored.getValue().getNationalIdNumber().startsWith("DLENC:v1:test:"));
        assertTrue(stored.getValue().getTaxCode().startsWith("DLENC:v1:test:"));
        assertEquals("079123456789", cipher.decryptSensitiveValue(stored.getValue().getNationalIdNumber(),
                driverId, "driver-dependent-national-id"));
    }

    @Test
    void rejectsDeductionEndWithoutStartBeforePersistence() {
        assertThrows(ApiException.class, () -> service.create(driverId, new CreateDriverDependentRequest(
                "Nguyễn An", "Child", null, null, null, null, LocalDate.of(2025, 1, 1))));
        verify(dependents, never()).saveAndFlush(any());
    }
}
