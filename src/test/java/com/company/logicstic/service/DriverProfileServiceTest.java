package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.dto.driverprofile.UpsertDriverProfileRequest;
import com.company.logicstic.entity.DriverProfile;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverProfileMapper;
import com.company.logicstic.repository.DriverProfileRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverProfileServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverProfileRepository profiles;
    private DriverProfileService service;
    private DriverPiiCipher cipher;

    @BeforeEach
    void setUp() {
        profiles = mock(DriverProfileRepository.class);
        var employees = mock(EmployeeRepository.class);
        var properties = new DriverPiiProperties();
        properties.setActiveKeyId("test");
        properties.setEncryptionKeys("test:" + Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        properties.setIndexKey(Base64.getEncoder().encodeToString(
                "index-key-0123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        cipher = new DriverPiiCipher(properties);
        service = new DriverProfileService(profiles, employees, cipher, new DriverProfileMapper(cipher),
                mock(CurrentUserService.class));
        var driver = new Employee();
        driver.setId(driverId);
        var role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(profiles.findByEmployee_Id(driverId)).thenReturn(Optional.empty());
        when(profiles.saveAndFlush(any(DriverProfile.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void upsertEncryptsIdentifiersAndReturnsOnlyMasks() {
        var view = service.upsert(driverId, request("123456789012", "1234567890", null));
        assertEquals("****9012", view.nationalIdNumber());
        assertEquals("****7890", view.personalTaxCode());
        var stored = org.mockito.ArgumentCaptor.forClass(DriverProfile.class);
        verify(profiles).saveAndFlush(stored.capture());
        assertTrue(stored.getValue().getNationalIdNumber().startsWith("DLENC:v1:test:"));
        assertTrue(stored.getValue().getPersonalTaxCode().startsWith("DLENC:v1:test:"));
        assertEquals("123456789012", cipher.decryptSensitiveValue(
                stored.getValue().getNationalIdNumber(), driverId, "driver-national-id"));
        assertEquals("1234567890", cipher.decryptSensitiveValue(
                stored.getValue().getPersonalTaxCode(), driverId, "driver-personal-tax-code"));
    }

    @Test
    void rejectsDuplicateIdentifierAndFutureIssuedDateWithoutPersistence() {
        String hmac = cipher.sensitiveValueHmac("123456789012", "driver-national-id");
        when(profiles.existsByNationalIdNumberHmac(hmac)).thenReturn(true);
        ApiException duplicate = assertThrows(ApiException.class,
                () -> service.upsert(driverId, request("123456789012", null, null)));
        assertEquals(409, duplicate.getStatus().value());
        assertFalse(duplicate.getMessage().contains("123456789012"));

        ApiException futureDate = assertThrows(ApiException.class,
                () -> service.upsert(driverId, request(null, null, LocalDate.now().plusDays(1))));
        assertEquals(400, futureDate.getStatus().value());
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void preservesExistingIdentifierWhenMaskedValueIsReturnedByTheView() {
        DriverProfile stored = new DriverProfile();
        stored.setId(UUID.randomUUID());
        stored.setEmployee(employee());
        stored.setNationalIdNumber(cipher.encryptSensitiveValue("123456789012", driverId, "driver-national-id"));
        stored.setNationalIdNumberHmac(cipher.sensitiveValueHmac("123456789012", "driver-national-id"));
        when(profiles.findByEmployee_Id(driverId)).thenReturn(Optional.of(stored));
        when(profiles.existsByNationalIdNumberHmacAndIdNot(any(), any())).thenReturn(false);

        var view = service.upsert(driverId, request("****9012", null, null));
        assertEquals("****9012", view.nationalIdNumber());
        assertEquals("123456789012", cipher.decryptSensitiveValue(
                stored.getNationalIdNumber(), driverId, "driver-national-id"));
    }

    private Employee employee() {
        Employee employee = new Employee();
        employee.setId(driverId);
        return employee;
    }

    private static UpsertDriverProfileRequest request(String nationalId, String taxCode, LocalDate issuedDate) {
        return new UpsertDriverProfileRequest(LocalDate.of(1985, 5, 2), "MALE", "Vietnam", "MARRIED",
                "HIGH_SCHOOL", nationalId, issuedDate, "Hà Nội", taxCode, null, null, null, null);
    }
}
