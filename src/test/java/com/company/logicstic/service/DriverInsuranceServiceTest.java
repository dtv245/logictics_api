package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.dto.driverprofile.CreateDriverInsuranceRequest;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverInsuranceMapper;
import com.company.logicstic.repository.DriverInsuranceRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverInsuranceServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverInsuranceRepository insurances;
    private DriverInsuranceService service;
    private DriverPiiCipher cipher;

    @BeforeEach
    void setUp() {
        insurances = mock(DriverInsuranceRepository.class);
        var employees = mock(EmployeeRepository.class);
        var properties = new DriverPiiProperties();
        properties.setActiveKeyId("test");
        properties.setEncryptionKeys("test:" + Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        properties.setIndexKey(Base64.getEncoder().encodeToString(
                "index-key-0123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        cipher = new DriverPiiCipher(properties);
        service = new DriverInsuranceService(insurances, employees, cipher, new DriverInsuranceMapper(cipher),
                mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(insurances.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void encryptsBothInsuranceIdentifiersAndOnlyReturnsMasks() {
        var view = service.create(driverId, new CreateDriverInsuranceRequest("1234567890", "ABC1234567",
                "Bệnh viện A", LocalDate.of(2025, 1, 1), null, "ACTIVE", "ACTIVE", "ACTIVE",
                new BigDecimal("8000000.00"), LocalDate.of(2026, 12, 31), null));
        assertEquals("****7890", view.socialInsuranceNumber());
        assertEquals("****4567", view.healthInsuranceCardNumber());

        var stored = org.mockito.ArgumentCaptor.forClass(com.company.logicstic.entity.DriverInsurance.class);
        verify(insurances).saveAndFlush(stored.capture());
        assertTrue(stored.getValue().getSocialInsuranceNumber().startsWith("DLENC:v1:test:"));
        assertTrue(stored.getValue().getHealthInsuranceCardNumber().startsWith("DLENC:v1:test:"));
        assertEquals("1234567890", cipher.decryptSensitiveValue(stored.getValue().getSocialInsuranceNumber(),
                driverId, "driver-social-insurance-number"));
    }

    @Test
    void rejectsEndBeforeStartWithoutPersistence() {
        assertThrows(ApiException.class, () -> service.create(driverId, new CreateDriverInsuranceRequest(
                null, null, null, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 1, 1),
                null, null, null, null, null, null)));
        verify(insurances, never()).saveAndFlush(any());
    }
}
