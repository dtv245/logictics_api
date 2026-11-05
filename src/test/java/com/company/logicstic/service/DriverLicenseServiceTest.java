package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.dto.driverlicense.CreateDriverLicenseRequest;
import com.company.logicstic.entity.DriverLicense;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.exception.ApiException;
import com.company.logicstic.mapper.DriverLicenseMapper;
import com.company.logicstic.repository.DocumentRepository;
import com.company.logicstic.repository.DriverLicenseRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverLicenseServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverLicenseRepository licenses;
    private EmployeeRepository employees;
    private DriverLicenseService service;
    private DriverPiiCipher cipher;

    @BeforeEach
    void setUp() {
        licenses = mock(DriverLicenseRepository.class);
        employees = mock(EmployeeRepository.class);
        var documents = mock(DocumentRepository.class);
        var properties = new DriverPiiProperties();
        properties.setActiveKeyId("test");
        properties.setEncryptionKeys("test:" + Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        properties.setIndexKey(Base64.getEncoder().encodeToString("index-key-0123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        cipher = new DriverPiiCipher(properties);
        service = new DriverLicenseService(licenses, employees, documents, cipher,
                new DriverLicenseMapper(cipher), mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        var role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(licenses.saveAndFlush(any(DriverLicense.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createEncryptsLicenseAndReturnsOnlyMaskedNumber() {
        var view = service.create(driverId, request("VN1234567", OffsetDateTime.parse("2024-01-01T00:00:00Z"),
                OffsetDateTime.parse("2030-01-01T00:00:00Z"), 12));

        assertEquals("****4567", view.licenseNumber());
        var stored = org.mockito.ArgumentCaptor.forClass(DriverLicense.class);
        verify(licenses).saveAndFlush(stored.capture());
        assertTrue(stored.getValue().getLicenseNumber().startsWith("DLENC:v1:test:"));
        assertNotEquals("VN1234567", stored.getValue().getLicenseNumber());
        assertEquals(64, stored.getValue().getLicenseNumberHmac().length());
        assertEquals("VN1234567", cipher.decryptLicenseNumber(
                stored.getValue().getLicenseNumber(), driverId, stored.getValue().getIssuingCountry()));
    }

    @Test
    void rejectsExpiryBeforeIssueAndInvalidPointsBeforePersistence() {
        assertThrows(ApiException.class, () -> service.create(driverId, request("VN1234567",
                OffsetDateTime.parse("2025-01-01T00:00:00Z"), OffsetDateTime.parse("2024-01-01T00:00:00Z"), 12)));
        assertThrows(ApiException.class, () -> service.create(driverId, request("VN1234567",
                OffsetDateTime.parse("2024-01-01T00:00:00Z"), OffsetDateTime.parse("2030-01-01T00:00:00Z"), 13)));
        verify(licenses, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateLicenseWithoutLoggingOrEchoingInput() {
        String hmac = cipher.licenseNumberHmac("VN1234567", driverId, "VN");
        when(licenses.existsByEmployee_IdAndLicenseNumberHmacAndIssuingCountry(driverId, hmac, "VN"))
                .thenReturn(true);

        ApiException error = assertThrows(ApiException.class, () -> service.create(driverId, request("VN1234567",
                OffsetDateTime.parse("2024-01-01T00:00:00Z"), OffsetDateTime.parse("2030-01-01T00:00:00Z"), 12)));
        assertEquals(409, error.getStatus().value());
        assertFalse(error.getMessage().contains("VN1234567"));
        verify(licenses, never()).saveAndFlush(any());
    }

    private static CreateDriverLicenseRequest request(String number, OffsetDateTime issued, OffsetDateTime expiry, Integer points) {
        return new CreateDriverLicenseRequest(number, "C", "", "VN", null, issued, expiry,
                "Hà Nội", points, null, null, null, null);
    }
}
