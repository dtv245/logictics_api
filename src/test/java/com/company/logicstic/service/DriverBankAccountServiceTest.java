package com.company.logicstic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.company.logicstic.config.DriverPiiProperties;
import com.company.logicstic.dto.driverprofile.CreateDriverBankAccountRequest;
import com.company.logicstic.entity.DriverBankAccount;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.TenantRole;
import com.company.logicstic.mapper.DriverBankAccountMapper;
import com.company.logicstic.repository.DriverBankAccountRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverBankAccountServiceTest {
    private final UUID driverId = UUID.randomUUID();
    private DriverBankAccountRepository accounts;
    private DriverBankAccountService service;
    private DriverBankAccount oldPrimary;

    @BeforeEach
    void setUp() {
        accounts = mock(DriverBankAccountRepository.class);
        var employees = mock(EmployeeRepository.class);
        var properties = new DriverPiiProperties();
        properties.setActiveKeyId("test");
        properties.setEncryptionKeys("test:" + Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        properties.setIndexKey(Base64.getEncoder().encodeToString(
                "index-key-0123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        var cipher = new DriverPiiCipher(properties);
        service = new DriverBankAccountService(accounts, employees, cipher, new DriverBankAccountMapper(cipher),
                mock(CurrentUserService.class));
        Employee driver = new Employee();
        driver.setId(driverId);
        TenantRole role = new TenantRole();
        role.setNormalizedName("DRIVER");
        driver.setEmployeeType(com.company.logicstic.entity.EmployeeType.DRIVER);
        driver.setRole(role);
        oldPrimary = new DriverBankAccount();
        oldPrimary.setId(UUID.randomUUID());
        oldPrimary.setEmployee(driver);
        oldPrimary.setPrimary(true);
        oldPrimary.setActive(true);
        oldPrimary.setAccountNumber(cipher.encryptSensitiveValue("12345678", driverId, "driver-bank-account-number"));
        when(employees.findByIdForUpdate(driverId)).thenReturn(Optional.of(driver));
        when(accounts.findFirstByEmployee_IdAndPrimaryTrueAndActiveTrue(driverId)).thenReturn(Optional.of(oldPrimary));
        when(accounts.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void promotingNewBankAccountReleasesOldPrimaryAndMasksItsNumber() {
        var view = service.create(driverId, new CreateDriverBankAccountRequest("Ngân hàng A", "87654321",
                "NGUYEN AN", true));
        assertEquals("****4321", view.accountNumber());
        assertTrue(view.primary());
        assertFalse(oldPrimary.isPrimary());
        var saved = org.mockito.ArgumentCaptor.forClass(DriverBankAccount.class);
        verify(accounts, times(2)).saveAndFlush(saved.capture());
        assertSame(oldPrimary, saved.getAllValues().getFirst());
        assertTrue(saved.getAllValues().getLast().isPrimary());
    }
}
