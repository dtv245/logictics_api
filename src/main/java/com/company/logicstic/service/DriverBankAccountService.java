package com.company.logicstic.service;

import com.company.logicstic.common.PaginationValidator;
import com.company.logicstic.dto.CurrentUserResponse;
import com.company.logicstic.dto.PagedResponse;
import com.company.logicstic.dto.driverprofile.CreateDriverBankAccountRequest;
import com.company.logicstic.dto.driverprofile.DriverBankAccountSensitiveView;
import com.company.logicstic.dto.driverprofile.DriverBankAccountView;
import com.company.logicstic.dto.driverprofile.UpdateDriverBankAccountRequest;
import com.company.logicstic.entity.DriverBankAccount;
import com.company.logicstic.entity.Employee;
import com.company.logicstic.entity.EmployeeType;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.ConflictException;
import com.company.logicstic.exception.ForbiddenException;
import com.company.logicstic.exception.ResourceNotFoundException;
import com.company.logicstic.mapper.DriverBankAccountMapper;
import com.company.logicstic.repository.DriverBankAccountRepository;
import com.company.logicstic.repository.EmployeeRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class DriverBankAccountService {
    private static final String DRIVER_ROLE = "DRIVER";
    private static final String ACCOUNT_CONTEXT = "driver-bank-account-number";
    private final DriverBankAccountRepository accounts;
    private final EmployeeRepository employees;
    private final DriverPiiCipher piiCipher;
    private final DriverBankAccountMapper mapper;
    private final CurrentUserService currentUserService;

    public DriverBankAccountService(DriverBankAccountRepository accounts, EmployeeRepository employees,
            DriverPiiCipher piiCipher, DriverBankAccountMapper mapper, CurrentUserService currentUserService) {
        this.accounts = accounts;
        this.employees = employees;
        this.piiCipher = piiCipher;
        this.mapper = mapper;
        this.currentUserService = currentUserService;
    }

    public PagedResponse<DriverBankAccountView> list(UUID driverId, int page, int pageSize,
            String orderBy, boolean descending) {
        requireDriver(driverId);
        String property = switch (orderBy == null ? "createdAt" : orderBy) {
            case "bankName" -> "bankName";
            case "createdAt" -> "createdAt";
            case "primary" -> "primary";
            default -> throw new BadRequestException("INVALID_SORT_FIELD", "Choose a supported bank account sort field");
        };
        var pageable = PaginationValidator.pageRequest(page, pageSize,
                Sort.by(descending ? Sort.Direction.DESC : Sort.Direction.ASC, property));
        return PagedResponse.from(accounts.findByEmployee_Id(driverId, pageable).map(mapper::toView));
    }

    public PagedResponse<DriverBankAccountView> listMine(int page, int pageSize,
            String orderBy, boolean descending) {
        return list(requireDriverIdentity().employeeId(), page, pageSize, orderBy, descending);
    }

    public DriverBankAccountView get(UUID driverId, UUID accountId) {
        requireDriver(driverId);
        return mapper.toView(requireAccount(driverId, accountId));
    }

    public DriverBankAccountSensitiveView getSensitive(UUID driverId, UUID accountId) {
        requireDriver(driverId);
        return mapper.toSensitiveView(requireAccount(driverId, accountId));
    }

    @Transactional
    public DriverBankAccountView create(UUID driverId, CreateDriverBankAccountRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverBankAccount account = new DriverBankAccount();
        account.setEmployee(driver);
        apply(account, driverId, request.bankName(), request.accountNumber(), request.accountHolderName());
        account.setActive(true);
        if (Boolean.TRUE.equals(request.primary())) clearExistingPrimary(driverId, null);
        account.setPrimary(Boolean.TRUE.equals(request.primary()));
        return mapper.toView(save(account));
    }

    @Transactional
    public DriverBankAccountView update(UUID driverId, UUID accountId, UpdateDriverBankAccountRequest request) {
        Employee driver = requireDriverForUpdate(driverId);
        DriverBankAccount account = requireAccount(driverId, accountId);
        apply(account, driverId, request.bankName(), request.accountNumber(), request.accountHolderName());
        if (Boolean.TRUE.equals(request.primary()) && Boolean.TRUE.equals(request.active())) {
            clearExistingPrimary(driverId, accountId);
        }
        account.setActive(Boolean.TRUE.equals(request.active()));
        account.setPrimary(Boolean.TRUE.equals(request.primary()) && account.isActive());
        account.setEmployee(driver);
        return mapper.toView(save(account));
    }

    @Transactional
    public void deactivate(UUID driverId, UUID accountId) {
        requireDriverForUpdate(driverId);
        DriverBankAccount account = requireAccount(driverId, accountId);
        account.setActive(false);
        account.setPrimary(false);
        save(account);
    }

    private void clearExistingPrimary(UUID driverId, UUID exceptId) {
        accounts.findFirstByEmployee_IdAndPrimaryTrueAndActiveTrue(driverId)
                .filter(existing -> !existing.getId().equals(exceptId))
                .ifPresent(existing -> {
                    existing.setPrimary(false);
                    save(existing); // Release the partial unique index before promoting the next account.
                });
    }

    private void apply(DriverBankAccount account, UUID driverId, String bankName, String submittedNumber,
            String accountHolderName) {
        account.setBankName(bankName.trim());
        account.setAccountHolderName(accountHolderName.trim());
        String normalized = submittedNumber.trim();
        String oldClear = piiCipher.decryptSensitiveValue(account.getAccountNumber(), driverId, ACCOUNT_CONTEXT);
        if (normalized.matches("\\*{4}[A-Za-z0-9]{4}")) {
            if (!StringUtils.hasText(oldClear) || !mask(oldClear).equals(normalized.toUpperCase(Locale.ROOT))) {
                throw new BadRequestException("MASKED_IDENTIFIER_CANNOT_BE_CHANGED",
                        "A masked bank account number can only be sent back unchanged");
            }
            return;
        }
        if (!normalized.matches("[A-Za-z0-9 -]{8,34}")) {
            throw new BadRequestException("INVALID_BANK_ACCOUNT_NUMBER", "The supplied bank account number is invalid");
        }
        String canonical = normalized.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
        account.setAccountNumber(piiCipher.encryptSensitiveValue(canonical, driverId, ACCOUNT_CONTEXT));
        account.setAccountNumberHmac(piiCipher.sensitiveValueHmac(canonical, ACCOUNT_CONTEXT));
    }

    private DriverBankAccount save(DriverBankAccount account) {
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException conflict) {
            throw ConflictException.forUniqueField("primary", "Only one active primary bank account is allowed");
        }
    }

    private DriverBankAccount requireAccount(UUID driverId, UUID accountId) {
        return accounts.findByIdAndEmployee_Id(accountId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver bank account not found: " + accountId));
    }

    private Employee requireDriver(UUID id) {
        Employee driver = employees.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private Employee requireDriverForUpdate(UUID id) {
        Employee driver = employees.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + id));
        if (driver.getEmployeeType() != EmployeeType.DRIVER) {
            throw new ResourceNotFoundException("Driver not found: " + id);
        }
        return driver;
    }

    private CurrentUserResponse requireDriverIdentity() {
        CurrentUserResponse identity = currentUserService.requireMappedEmployee();
        if (!identity.roles().contains(DRIVER_ROLE)) {
            throw new ForbiddenException("Driver self-service is restricted to driver accounts");
        }
        return identity;
    }

    private static String mask(String value) {
        return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
    }
}
